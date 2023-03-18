package net.siggijons.gradle.graphuntangler

import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jgrapht.alg.TransitiveReduction
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.graph.AbstractGraph
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import org.jgrapht.nio.matrix.MatrixExporter
import org.jgrapht.traverse.TopologicalOrderIterator
import java.io.File

abstract class AnalyzeModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val configurationsToAnalyze: SetProperty<String>

    @get:Optional
    @get:InputFile
    abstract val ownersFile: RegularFileProperty

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

    @get:OutputDirectory
    abstract val outputProjectSubgraphs: DirectoryProperty

    @TaskAction
    fun run() {
        val dependencyPairs = project.rootProject
            .dependencyPairs(configurationsToAnalyze.get())

        val owners = readOwnersIntoMap()

        val frequencyMap = readFrequencyMap()
        val graph = dependencyPairs.toJGraphTGraph(
            ownerMap = owners,
            frequencyMap = frequencyMap
        )

        val nodeStatistics = graph.nodeStatistics()
        val heightGraph = heightGraph(graph, nodeStatistics.nodes)

        createCoOccurrenceMatrix(graph)

        val projectsDir = outputProjectSubgraphs.get().asFile
        projectsDir.deleteRecursively()
        projectsDir.mkdirs()
        writeProjectSubgraphs(graph, projectsDir)
        writeIsolatedSubgraphs(graph, projectsDir)

        writeStatistics(nodeStatistics)
        writeDotGraph(graph, outputDot.get().asFile)
        writeDotGraph(heightGraph, outputDotHeight.get().asFile)

        TransitiveReduction.INSTANCE.reduce(graph)
        writeDotGraph(graph, outputDotReduced.get().asFile)
    }

    // TODO: verify functionality without owners file and use extension properly
    private fun readOwnersIntoMap(): Map<String, String> {
        val file = ownersFile.orNull ?: return emptyMap()
        return OwnerFileReader().read(file.asFile)
    }

    private fun writeProjectSubgraphs(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>,
        outputDir: File
    ) {
        graph.vertexSet().forEach { vertex ->
            val descendants = graph.getDescendants(vertex)
            val subgraph = AsSubgraph(graph, descendants + vertex)
            writeDotGraph(subgraph, File(outputDir, "${vertex.safeFileName}.gv"))

            val dag = DirectedAcyclicGraph.createBuilder<DependencyNode, DependencyEdge>(
                DependencyEdge::class.java
            ).addGraph(subgraph).build()

            val dagStats = dag.nodeStatistics()
            val subgraphHeightGraph = heightGraph(dag, dagStats.nodes)
            writeDotGraph(
                subgraphHeightGraph,
                File(outputDir, "${vertex.safeFileName}-height.gv")
            )
        }
    }

    private val DependencyNode.safeFileName: String
        get() = project.replace(":", "_")

    /**
     * Creates a subgraph for every module, or vertex, in the graph that only includes
     * vertices that are either ancestors or descendants of the vertex, as well as the vertex
     * itself.
     *
     * This creates a representation that makes it possible to reason how a specific module
     * interacts with the rest of the graph and can help visualize the value captured by RTTD.
     *
     * Additionally a csv, isolated-subgraph-size.csv, is created capturing the size of each
     * subgraph for further analysis.
     *
     * @see [NodeStatistics.rebuiltTargetsByTransitiveDependencies]
     */
    private fun writeIsolatedSubgraphs(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>,
        outputDir: File
    ) {
        val isolatedSubgraphSize = mutableListOf<Triple<DependencyNode, Int, Int>>()
        graph.vertexSet().forEach { vertex ->
            val ancestors = graph.getAncestors(vertex)
            val descendants = graph.getDescendants(vertex)

            val builder = DirectedAcyclicGraph.createBuilder<DependencyNode, DependencyEdge>(
                DependencyEdge::class.java
            )

            builder.addGraph(graph)

            val disconnected = graph.vertexSet() - ancestors - descendants - vertex
            disconnected.forEach {
                builder.removeVertex(it)
            }

            val isolatedDag = builder.build()

            val graphSize = graph.vertexSet().size
            val isolatedDagSize = isolatedDag.vertexSet().size
            isolatedSubgraphSize.add(Triple(vertex, isolatedDagSize, graphSize))

            writeDotGraph(
                isolatedDag,
                File(outputDir, "${vertex.safeFileName}-isolated.gv")
            )

            TransitiveReduction.INSTANCE.reduce(isolatedDag)

            writeDotGraph(
                isolatedDag,
                File(outputDir, "${vertex.safeFileName}-isolated-reduced.gv")
            )
        }

        with(File(outputDir, "isolated-subgraph-size.csv").printWriter()) {
            println("vertex,isolatedDagSize,graphSize\n")
            isolatedSubgraphSize.forEach {
                println("${it.first.project},${it.second},${it.third}\n")
            }
            flush()
        }
    }

    private fun readFrequencyMap(): Map<String, Int> {
        return changeFrequencyFile.get().asFile.readLines().drop(1).associate {
            val s = it.split(",")
            s[0] to s[1].toInt()
        }
    }

    private fun createCoOccurrenceMatrix(graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>) {
        val exporter = MatrixExporter<DependencyNode, DependencyEdge>(
            MatrixExporter.Format.SPARSE_ADJACENCY_MATRIX
        ) { v -> v.project }
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
                    "rebuiltTargetsByTransitiveDependencies"
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
                    it.rebuiltTargetsByTransitiveDependencies
                )
            }
        }.renderText().also {
            file.appendText(it)
            file.appendText("\n\n")
        }
    }

    private fun writeDotGraph(
        graph: AbstractGraph<DependencyNode, DependencyEdge>,
        file: File
    ) {
        val exporter = DOTExporter<DependencyNode, DependencyEdge> { vertex ->
            vertex.project.replace("-", "_").replace(".", "_").replace(":", "_")
        }

        exporter.setVertexAttributeProvider { v ->
            val color = v.normalizedChangeRate?.rateColor()

            var label = v.changeRate?.let {
                "%s | %d".format(v.project, it)
            } ?: v.project
            if (v.owner != null) {
                label += "\n${v.owner}"
            }

            mapOf(
                "label" to DefaultAttribute.createAttribute(label),
                "style" to DefaultAttribute.createAttribute("filled"),
                "fillcolor" to DefaultAttribute.createAttribute(color)
            )
        }

        file.delete()
        exporter.exportGraph(graph, file)
    }

    /**
     * Calculate statistics for graph.
     */
    private fun DirectedAcyclicGraph<DependencyNode, DependencyEdge>.nodeStatistics():
            GraphStatistics {
        val betweennessCentrality = BetweennessCentrality(this).scores
        val heights = heights()
        val iterator = TopologicalOrderIterator(this)
        val nodes = mutableListOf<NodeStatistics>()
        while (iterator.hasNext()) {
            val node = iterator.next()
            val descendants = getDescendants(node)
            val ancestors = getAncestors(node)
            val descendantsChangeRate = descendants.sumOf { it.changeRate ?: 0 }
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
                changeRate = node.changeRate ?: 0,
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

    private fun DirectedAcyclicGraph<DependencyNode, DependencyEdge>.heights(): Heights<DependencyNode> {
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
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>,
        nodes: List<NodeStatistics>
    ): DirectedAcyclicGraph<DependencyNode, DependencyEdge> {
        val g = DirectedAcyclicGraph<DependencyNode, DependencyEdge>(DependencyEdge::class.java)

        val added = mutableSetOf<DependencyNode>()
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
    private fun List<Triple<Project, Project, String>>.toJGraphTGraph(
        ownerMap: Map<String, String>,
        frequencyMap: Map<String, Int>
    ): DirectedAcyclicGraph<DependencyNode, DependencyEdge> {
        val g = DirectedAcyclicGraph<DependencyNode, DependencyEdge>(DependencyEdge::class.java)

        val maxChangeRate = frequencyMap.values.maxOrNull()?.toDouble() ?: 1.0

        val projectMap = flatMap { listOf(it.first, it.second) }
            .distinct()
            .associateWith { project ->
                DependencyNode(
                    project = project.path,
                    // TODO: create better abstractions.
                    // TODO: assumes matching on first
                    // TODO: assumes GitHub's @Org/Team name and that dropping org is desired
                    owner = ownerMap.entries.firstOrNull {
                        project.path.startsWith(it.key)
                    }?.value?.split("/")?.lastOrNull(),
                    changeRate = frequencyMap[project.path],
                    normalizedChangeRate = frequencyMap[project.path]?.let { rate ->
                        rate / maxChangeRate
                    }
                )
            }

        forEach { (a, b, label) ->
            try {
                val nodeA = projectMap[a]
                val nodeB = projectMap[b]
                g.addVertex(nodeA)
                g.addVertex(nodeB)
                g.addEdge(nodeA, nodeB, DependencyEdge(label = label))
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Error when adding $a -> $b", e)
            }
        }
        return g
    }
}