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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jgrapht.alg.TransitiveReduction
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import org.jgrapht.nio.matrix.MatrixExporter
import org.jgrapht.traverse.TopologicalOrderIterator
import java.lang.IllegalStateException

abstract class AnalyzeModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val configurationsToAnalyze: SetProperty<String>

    @get:Optional
    @get:Input
    abstract val rootNode: Property<String?>

    @get:InputFile
    abstract val changeFrequencyFile: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val outputDot: RegularFileProperty

    @get:OutputFile
    abstract val outputDotHeight: RegularFileProperty

    @get:OutputFile
    abstract val outputDotReduced: RegularFileProperty

    @get:OutputFile
    abstract val outputAdjacencyMatrix: RegularFileProperty

    @TaskAction
    fun run() {
        val dependencyPairs = project.rootProject
            .dependencyPairs(configurationsToAnalyze.get())

        val graph = dependencyPairs.toJGraphTGraph()

        val frequencyMap = readFrequencyMap()

        val nodeStatistics = graph.nodeStatistics(frequencyMap)
        val heightGraph = heightGraph(graph, nodeStatistics.nodes)

        createCoOccurrenceMatrix(graph)

        writeStatistics(nodeStatistics)
        writeDotGraph(graph, outputDot.get())
        writeDotGraph(heightGraph, outputDotHeight.get())

        TransitiveReduction.INSTANCE.reduce(graph)
        writeDotGraph(graph, outputDotReduced.get())
    }

    private fun readFrequencyMap(): Map<String, Int> {
        return changeFrequencyFile.get().asFile.readLines().drop(1).associate {
            val s = it.split(",")
            s[0] to s[1].toInt()
        }
    }

    private fun createCoOccurrenceMatrix(graph: DirectedAcyclicGraph<String, DependencyEdge>) {
        val exporter = MatrixExporter<String, DependencyEdge>(
            MatrixExporter.Format.SPARSE_ADJACENCY_MATRIX
        ) { v -> v }
        exporter.exportGraph(graph, outputAdjacencyMatrix.get().asFile)
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
                    "height",
                    "ancestors",
                    "descendants",
                    "changeRate",
                    "descendantsChangeRate",
                    "spotifyBadness"
                )
            }
            graphStatistics.nodes.forEach {
                row(
                    it.node,
                    "%.2f".format(it.betweennessCentrality),
                    it.degree,
                    it.inDegree,
                    it.outDegree,
                    it.height,
                    it.ancestors,
                    it.descendants,
                    it.changeRate,
                    it.descendantsChangeRate,
                    it.spotifyBadness
                )
            }
        }.renderText().also {
            file.appendText(it)
            file.appendText("\n\n")
        }
    }

    private fun writeDotGraph(
        graph: DirectedAcyclicGraph<String, DependencyEdge>,
        regularFile: RegularFile
    ) {
        val exporter = DOTExporter<String, DependencyEdge> { vertex ->
            vertex.replace("-", "_").replace(".", "_").replace(":", "_")
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
    private fun DirectedAcyclicGraph<String, DependencyEdge>.nodeStatistics(
        frequencyMap: Map<String, Int>
    ): GraphStatistics {
        val betweennessCentrality = BetweennessCentrality(this).scores
        val heights = heights()
        val iterator = TopologicalOrderIterator(this)
        val nodes = mutableListOf<NodeStatistics>()
        while (iterator.hasNext()) {
            val node = iterator.next()
            val descendants = getDescendants(node)
            val ancestors = getAncestors(node)
            val descendantsChangeRate = descendants.sumOf { frequencyMap[it] ?: 0 }
            val s = NodeStatistics(
                node = node,
                betweennessCentrality = requireNotNull(betweennessCentrality[node]) {
                    "betweennessCentrality not found for $node"
                },
                degree = degreeOf(node),
                inDegree = inDegreeOf(node),
                outDegree = outDegreeOf(node),
                height = heights.heightMap[node] ?: -1,
                ancestors = ancestors.size,
                descendants = descendants.size,
                changeRate = frequencyMap[node] ?: 0,
                descendantsChangeRate = descendantsChangeRate
            )
            nodes.add(s)
        }

        return GraphStatistics(
            nodes = nodes
        )
    }

    class HeightMeasurer<V, E>(
        private val graph: DirectedAcyclicGraph<V, E>
    ) {

        private val heightMap = LinkedHashMap<V, Int>()

        fun calculateHeightMap(): Map<V, Int> {
            graph.vertexSet().forEach { v ->
                heightOf(v)
            }
            return heightMap.toMap()
        }

        private fun heightOf(v: V): Int {
            val cached = heightMap[v]
            if (cached != null) {
                return cached
            }

            val descendants = graph.getDescendants(v)
            val height = if (descendants.isEmpty()) {
                0
            } else {
                descendants.maxOf { heightOf(it) } + 1
            }
            heightMap[v] = height
            return height
        }
    }

    private fun DirectedAcyclicGraph<String, DependencyEdge>.heights(): Heights<String> {
        val map = HeightMeasurer(graph = this).calculateHeightMap()
        return Heights(map)
    }

    data class Heights<V>(
        val heightMap: Map<V, Int>
    )

    /**
     * Generate a graph that consists only of nodes that participate in the longest paths
     * across the graph. This can be useful when there are multiple longest paths in a graph.
     * The algorithm is naive and unproven.
     */
    private fun heightGraph(
        graph: DirectedAcyclicGraph<String, DependencyEdge>,
        nodes: List<NodeStatistics>
    ): DirectedAcyclicGraph<String, DependencyEdge> {
        val g = DirectedAcyclicGraph<String, DependencyEdge>(DependencyEdge::class.java)

        val added = mutableSetOf<String>()
        val byHeight = nodes.sortedByDescending { it.height }.groupBy { it.height }
        byHeight.forEach { (_, currentLevel) ->

            if (added.isEmpty()) {
                currentLevel.forEach {
                    g.addVertex(it.node)
                    added.add(it.node)
                }
            } else {
                val connectionsToPrevious = currentLevel.map { v ->
                    v.node to added.filter { u -> graph.containsEdge(u, v.node) }
                }.filter { it.second.isNotEmpty() }

                added.clear()
                connectionsToPrevious.forEach { (u, vs) ->
                    vs.forEach {
                        g.addVertex(u)
                        g.addEdge(it, u, DependencyEdge(label = "Height Neighbor"))
                        added.add(u)
                    }
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
    private fun List<Triple<Project, Project, String>>.toJGraphTGraph(): DirectedAcyclicGraph<String, DependencyEdge> {
        val g = DirectedAcyclicGraph<String, DependencyEdge>(DependencyEdge::class.java)
        forEach { (a, b, label) ->
            try {
                g.addVertex(a.path)
                g.addVertex(b.path)
                g.addEdge(a.path, b.path, DependencyEdge(label = label))
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Error when adding $a -> $b", e)
            }
        }
        return g
    }
}