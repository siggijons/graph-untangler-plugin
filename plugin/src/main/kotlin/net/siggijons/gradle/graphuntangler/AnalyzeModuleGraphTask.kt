package net.siggijons.gradle.graphuntangler

import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import net.siggijons.gradle.graphuntangler.model.DependencyEdge
import net.siggijons.gradle.graphuntangler.model.DependencyNode
import net.siggijons.gradle.graphuntangler.model.GraphUntangler
import net.siggijons.gradle.graphuntangler.model.IsolatedSubgraphDetails
import net.siggijons.gradle.graphuntangler.model.SubgraphDetails
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
import org.jgrapht.graph.AbstractGraph
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import org.jgrapht.nio.matrix.MatrixExporter
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
        // Inputs
        logger.quiet("Analyzing Module Graph")
        val dependencyPairs = project.rootProject
            .dependencyPairs(configurationsToAnalyze.get())
        val owners = readOwners()
        val frequencyMap = readFrequencyMap()

        logger.quiet("Creating Graph")
        val graph = dependencyPairs.toJGraphTGraph(
            owners = owners,
            frequencyMap = frequencyMap
        )

        // Calculate Statistics
        logger.quiet("Calculating Statistics")
        val graphUntangler = GraphUntangler()
        val nodeStatistics = graphUntangler.nodeStatistics(graph)
        val reducedGraph = graphUntangler.safeReduce(graph)
        val heightGraph = graphUntangler.heightGraph(graph, nodeStatistics.nodes)
        val subgraphs = graphUntangler.analyzeSubgraphs(graph)
        val isolatedSubgraphs = graphUntangler.isolateSubgraphs(graph)

        // Clean
        val projectsDir = outputProjectSubgraphs.get().asFile
        projectsDir.deleteRecursively()
        projectsDir.mkdirs()

        val statisticsOutput = output.get().asFile
        statisticsOutput.delete()

        // Write Stats
        logger.quiet("Writing reports")
        logger.quiet("Statistics $statisticsOutput")
        writeStatistics(nodeStatistics, statisticsOutput)

        writeDotGraph(graph, outputDot.get().asFile)
        writeDotGraph(heightGraph, outputDotHeight.get().asFile)
        writeDotGraph(reducedGraph, outputDotReduced.get().asFile)

        writeCoOccurrenceMatrix(graph)
        writeProjectSubgraphs(subgraphs, projectsDir)
        writeIsolatedSubgraphs(isolatedSubgraphs, projectsDir)
    }

    // TODO: verify functionality without owners file and use extension properly
    private fun readOwners(): Owners {
        val file = ownersFile.orNull ?: return Owners(ownerMap = emptyMap())
        return OwnerFileReader().read(file.asFile)
    }

    private fun writeProjectSubgraphs(
        graphs: List<SubgraphDetails>,
        outputDir: File
    ) {
        graphs.forEach { subgraph ->
            with(subgraph) {
                writeDotGraph(
                    subgraph.subgraph,
                    File(outputDir, "${vertex.safeFileName}.gv")
                )

                writeDotGraph(
                    subgraphHeightGraph,
                    File(outputDir, "${vertex.safeFileName}-height.gv")
                )
                writeDescendantsCounts(
                    vertex = vertex,
                    descendants = descendants,
                    outputDir = outputDir
                )
            }
        }
    }

    /**
     * Write csv files with stats about the ownership of descendants.
     */
    private fun writeDescendantsCounts(
        vertex: DependencyNode,
        descendants: Set<DependencyNode>,
        outputDir: File
    ) {
        val descendantsMap = descendants.groupBy(
            keySelector = { it.owner },
            valueTransform = { it.project }
        )

        with(
            File(outputDir, "${vertex.safeFileName}-descendants-owners-count.csv").printWriter()
        ) {
            println("owner,modules")
            descendantsMap.forEach { (owner, projects) ->
                println("$owner,${projects.size}")
            }
            flush()
        }

        with(
            File(outputDir, "${vertex.safeFileName}-descendants-owners.csv").printWriter()
        ) {
            println("owner,module")
            descendantsMap.forEach { (owner, projects) ->
                projects.forEach {
                    println("$owner,$it")
                }
            }
            flush()
        }
    }

    private val DependencyNode.safeFileName: String
        get() = project.replace(":", "_")

    private fun writeIsolatedSubgraphs(
        graphs: List<IsolatedSubgraphDetails>,
        outputDir: File
    ) {
        graphs.forEach { details ->
            writeDotGraph(
                graph = details.isolatedDag,
                file = File(outputDir, "${details.vertex.safeFileName}-isolated.gv"),
                colorMode = ColorMode.OWNER
            )

            writeDotGraph(
                graph = details.reducedDag,
                file = File(outputDir, "${details.vertex.safeFileName}-isolated-reduced.gv"),
                colorMode = ColorMode.OWNER
            )
        }

        with(File(outputDir, "isolated-subgraph-size.csv").printWriter()) {
            println("vertex,isolatedDagSize,fullGraphSize\n")
            graphs.forEach {
                println("${it.vertex.project},${it.isolatedDagSize},${it.fullGraphSize}\n")
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

    private fun writeCoOccurrenceMatrix(graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>) {
        val exporter = MatrixExporter<DependencyNode, DependencyEdge>(
            MatrixExporter.Format.SPARSE_ADJACENCY_MATRIX
        ) { v -> v.project }
        exporter.exportGraph(graph, outputAdjacencyMatrix.get().asFile)
    }

    private fun writeStatistics(
        graphStatistics: GraphStatistics,
        file: File
    ) {
        table {
            cellStyle {
                paddingLeft = 1
                paddingRight = 1
            }
            header {
                row(
                    "node",
                    "owner",
                    "betweennessCentrality",
                    "degree",
                    "inDegree",
                    "outDegree",
                    "height",
                    "ancestors",
                    "descendants",
                    "changeRate",
                    "descendantsChangeRate",
                    "rebuiltTargetsByTransitiveDependencies",
                    "nonSelfOwnedDescendants",
                    "uniqueNonSelfOwnedDescendants",
                    "nonSelfOwnedAncestors",
                    "uniqueNonSelfOwnedAncestors"
                )
            }
            graphStatistics.nodes.forEach {
                row(
                    it.node.project,
                    it.node.owner,
                    "%.2f".format(it.betweennessCentrality),
                    it.degree,
                    it.inDegree,
                    it.outDegree,
                    it.height,
                    it.ancestors,
                    it.descendants,
                    it.changeRate,
                    it.descendantsChangeRate,
                    it.rebuiltTargetsByTransitiveDependencies,
                    it.ownershipInfo?.nonSelfOwnedDescendants,
                    it.ownershipInfo?.uniqueNonSelfOwnedDescendants,
                    it.ownershipInfo?.nonSelfOwnedAncestors,
                    it.ownershipInfo?.uniqueNonSelfOwnedAncestors
                )
            }
        }.renderText().also {
            file.appendText(it)
            file.appendText("\n\n")
        }
    }

    enum class ColorMode { CHANGE_RATE, OWNER }

    private fun writeDotGraph(
        graph: AbstractGraph<DependencyNode, DependencyEdge>,
        file: File,
        colorMode: ColorMode = ColorMode.CHANGE_RATE
    ) {
        val exporter = DOTExporter<DependencyNode, DependencyEdge> { vertex ->
            vertex.project.replace("-", "_").replace(".", "_").replace(":", "_")
        }

        val colorMap = if (colorMode == ColorMode.OWNER) {
            val owners = graph.vertexSet()
                .groupingBy { it.owner.orEmpty() }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
                .map { it.first }
            seriesColors(owners)
        } else {
            emptyMap()
        }

        exporter.setVertexAttributeProvider { v ->
            val color = when (colorMode) {
                ColorMode.CHANGE_RATE -> v.normalizedChangeRate?.rateColor()
                ColorMode.OWNER -> colorMap[v.owner]
            }

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
        owners: Owners,
        frequencyMap: Map<String, Int>
    ): DirectedAcyclicGraph<DependencyNode, DependencyEdge> {
        val g = DirectedAcyclicGraph<DependencyNode, DependencyEdge>(DependencyEdge::class.java)

        val maxChangeRate = frequencyMap.values.maxOrNull()?.toDouble() ?: 1.0

        val projectMap = flatMap { listOf(it.first, it.second) }
            .distinct()
            .associateWith { project ->
                DependencyNode(
                    project = project.path,
                    owner = owners.findOwner(project.path),
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