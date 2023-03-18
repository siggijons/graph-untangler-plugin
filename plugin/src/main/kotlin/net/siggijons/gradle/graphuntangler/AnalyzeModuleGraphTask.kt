package net.siggijons.gradle.graphuntangler

import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jgrapht.alg.TransitiveReduction
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import org.jgrapht.traverse.BreadthFirstIterator

abstract class AnalyzeModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val configurationsToAnalyze: SetProperty<String>

    @get:Optional
    @get:Input
    abstract val rootNode: Property<String?>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val outputDot: RegularFileProperty

    @get:OutputFile
    abstract val outputDotDepth: RegularFileProperty

    @get:OutputFile
    abstract val outputDotReduced: RegularFileProperty

    @TaskAction
    fun run() {
        val graph = project.rootProject
            .dependencyPairs(configurationsToAnalyze.get())
            .toJGraphTGraph()

        val nodeStatistics = graph.nodeStatistics()
        val depthGraph = depthGraph(graph, nodeStatistics.nodes)

        writeStatistics(nodeStatistics)
        writeDotGraph(graph, outputDot.get())
        writeDotGraph(depthGraph, outputDotDepth.get())

        TransitiveReduction.INSTANCE.reduce(graph)
        writeDotGraph(graph, outputDotReduced.get())
    }

    private fun writeStatistics(
        graphStatistics: GraphStatistics
    ) {
        val file = output.get().asFile
        file.delete()
        table {
            cellStyle {
                paddingLeft = 1
                paddingRight = 1
            }
            header {
                row(
                    "node",
                    "betweennessCentrality",
                    "degree",
                    "inDegree",
                    "outDegree",
                    "depth",
                    "eccentricity"
                )
            }
            graphStatistics.nodes.forEach {
                row(
                    it.node,
                    "%.2f".format(it.betweennessCentrality),
                    it.degree,
                    it.inDegree,
                    it.outDegree,
                    it.depth,
                    it.eccentricity
                )
            }
        }.renderText().also {
            file.appendText(it)
            file.appendText("\n\n")
        }
    }

    private fun writeDotGraph(
        graph: DefaultDirectedGraph<String, DependencyEdge>,
        regularFile: RegularFile
    ) {
        val exporter = DOTExporter<String, DependencyEdge> { vertex ->
            vertex.replace("-", "_").replace(".", "_")
        }

        exporter.setVertexAttributeProvider { v ->
            mapOf("label" to DefaultAttribute.createAttribute(v))
        }

        val file = regularFile.asFile
        file.delete()
        exporter.exportGraph(graph, file)
    }

    /**
     * Calculate statistics for graph
     *
     * A breath first iterator is used to traverse the graph and calculate the height using
     * all vertices with 0 in degree as the roots. This is untested for graphs with multiple roots
     * but it could work.
     */
    private fun DefaultDirectedGraph<String, DependencyEdge>.nodeStatistics(): GraphStatistics {
        val betweennessCentrality = BetweennessCentrality(this).scores
        val roots = vertexSet().filter {
            inDegreeOf(it) == 0
        }.filter {
            !rootNode.isPresent || it == rootNode.get()
        }

        if (roots.size > 1) {
            logger.warn(
                "More than one potential root found.\n" +
                        "\trootNode=${rootNode.orNull}.\n" +
                        "\troots=$roots\n" +
                        "First root will be used to calculate height. To configure use:\n" +
                        "untangler {\n" +
                        "\trootNode = \"app\"\n" +
                        "}"
            )
        }

        val measures = measure()

        logger.warn("$measures")

        val nodes = roots.first().let { root ->
            val iterator = BreadthFirstIterator(this, root)
            val stats = mutableListOf<NodeStatistics>()
            while (iterator.hasNext()) {
                val node = iterator.next()
                val s = NodeStatistics(
                    node = node,
                    betweennessCentrality = requireNotNull(betweennessCentrality[node]) {
                        "betweennessCentrality not found for $node"
                    },
                    degree = degreeOf(node),
                    inDegree = inDegreeOf(node),
                    outDegree = outDegreeOf(node),
                    depth = iterator.getDepth(node),
                    eccentricity = measures.eccentricityMap[node] ?: -1.0
                )
                stats.add(s)
            }
            stats
        }

        return GraphStatistics(
            nodes = nodes
        )
    }

    private fun DefaultDirectedGraph<String, DependencyEdge>.measure(): Measures<String> {
        val algorithm = FloydWarshallShortestPaths(this)
        val eccentricityMap = vertexSet().associateWith { u ->
            vertexSet().maxOf { v ->
                val pathWeight = algorithm.getPathWeight(u, v)
                if (pathWeight != Double.POSITIVE_INFINITY) pathWeight
                else 0.0
            }
        }
        val diameter = vertexSet().maxOf { eccentricityMap[it] ?: 0.0 }
        return Measures(
            diameter = diameter,
            eccentricityMap = eccentricityMap
        )
    }

    data class Measures<V>(
        val diameter: Double,
        val eccentricityMap: Map<V, Double>
    )

    private fun depthGraph(
        graph: DefaultDirectedGraph<String, DependencyEdge>,
        nodes: List<NodeStatistics>
    ): DefaultDirectedGraph<String, DependencyEdge> {
        val g = DefaultDirectedGraph<String, DependencyEdge>(DependencyEdge::class.java)
        nodes.forEach { g.addVertex(it.node) }

        val byDepth = nodes.groupBy { it.depth }
        byDepth.forEach { (height, currentLevel) ->
            val nextLevel = byDepth.getOrDefault(height + 1, emptyList())
            currentLevel.forEach { current ->
                val connected = nextLevel.filter { next ->
                    graph.containsEdge(current.node, next.node)
                }
                connected.forEach {
                    g.addEdge(current.node, it.node, DependencyEdge(label = "critical"))
                }
            }
        }
        return g
    }

    /**
     * Create a list of all dependency pairs for the matching configurations
     *
     * @param configurationsToAnalyze configuration names to analyze
     * @see [org.gradle.api.artifacts.Configuration.getName]
     */
    private fun Project.dependencyPairs(
        configurationsToAnalyze: Set<String>
    ): List<Triple<Project, Project, String>> {
        return subprojects.flatMap { project ->
            project.configurations
                .filter { configurationsToAnalyze.contains(it.name) }
                .flatMap { configuration ->
                    configuration.dependencies.filterIsInstance<ProjectDependency>()
                        .map { Triple(project, it.dependencyProject, configuration.name) }
                }
        }
    }

    /**
     * Create a DirectedGraph from all dependency pairs in a project
     */
    private fun List<Triple<Project, Project, String>>.toJGraphTGraph(): DefaultDirectedGraph<String, DependencyEdge> {
        val g = DefaultDirectedGraph<String, DependencyEdge>(DependencyEdge::class.java)
        forEach { (a, b, label) ->
            g.addVertex(a.name)
            g.addVertex(b.name)
            g.addEdge(a.name, b.name, DependencyEdge(label = label))
        }
        return g
    }
}