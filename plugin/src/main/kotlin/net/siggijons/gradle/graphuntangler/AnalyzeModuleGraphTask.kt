package net.siggijons.gradle.graphuntangler

import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import net.siggijons.gradle.graphuntangler.model.DependencyEdge
import net.siggijons.gradle.graphuntangler.model.DependencyNode
import net.siggijons.gradle.graphuntangler.model.GraphUntangler
import net.siggijons.gradle.graphuntangler.model.IsolatedSubgraphDetails
import net.siggijons.gradle.graphuntangler.model.SubgraphDetails
import net.siggijons.gradle.graphuntangler.writer.GraphvizWriter
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.nio.matrix.MatrixExporter
import java.io.File

abstract class AnalyzeModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val configurationsToAnalyze: SetProperty<String>

    @get:Optional
    @get:InputFile
    abstract val ownersFile: RegularFileProperty

    @get:InputFile
    abstract val changeFrequencyFile: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val outputCsv: RegularFileProperty

    @get:OutputFile
    abstract val outputDot: RegularFileProperty

    @get:OutputFile
    abstract val outputDotHeight: RegularFileProperty

    @get:OutputFile
    abstract val outputDotReduced: RegularFileProperty

    @get:OutputFile
    abstract val outputAdjacencyMatrix: RegularFileProperty

    @get:OutputFile
    abstract val outputIsolatedSubgraphSize: RegularFileProperty

    @get:OutputDirectory
    abstract val outputProjectSubgraphs: DirectoryProperty

    private val graphvizWriter = GraphvizWriter()

    @TaskAction
    fun run() {
        // Inputs
        logger.quiet("Analyzing Module Graph")
        val dependencyPairs = project.rootProject
            .dependencyPairs(configurationsToAnalyze.get())
        val owners = readOwners()
        logger.info("Read owners: ${owners.ownerMap}")
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

        val statisticsCsvOutput = outputCsv.get().asFile
        statisticsCsvOutput.delete()

        // Write Stats
        logger.quiet("Writing reports")
        logger.quiet("Statistics $statisticsOutput")
        writeStatistics(nodeStatistics, statisticsOutput)
        writeStatisticsCsv(nodeStatistics, statisticsCsvOutput)

        graphvizWriter.writeDotGraph(graph, outputDot.get().asFile)
        graphvizWriter.writeDotGraph(heightGraph, outputDotHeight.get().asFile)
        graphvizWriter.writeDotGraph(reducedGraph, outputDotReduced.get().asFile)

        writeCoOccurrenceMatrix(graph, outputAdjacencyMatrix.get().asFile)
        writeProjectSubgraphs(subgraphs, projectsDir)
        writeProjectSubgraphsDependantsCount(subgraphs, projectsDir)
        writeIsolatedSubgraphs(isolatedSubgraphs, projectsDir)
    }

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
                graphvizWriter.writeDotGraph(
                    subgraph.subgraph,
                    File(outputDir, "${vertex.safeFileName}.gv")
                )

                graphvizWriter.writeDotGraph(
                    subgraphHeightGraph,
                    File(outputDir, "${vertex.safeFileName}-height.gv")
                )
            }
        }
    }

    private fun writeProjectSubgraphsDependantsCount(
        graphs: List<SubgraphDetails>,
        outputDir: File
    ) {
        graphs.forEach { subgraph ->
            writeDescendantsCounts(
                vertex = subgraph.vertex,
                descendants = subgraph.descendants,
                outputDir = outputDir
            )
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
            graphvizWriter.writeDotGraph(
                graph = details.isolatedDag,
                file = File(outputDir, "${details.vertex.safeFileName}-isolated.gv"),
                colorMode = ColorMode.OWNER
            )

            graphvizWriter.writeDotGraph(
                graph = details.reducedDag,
                file = File(outputDir, "${details.vertex.safeFileName}-isolated-reduced.gv"),
                colorMode = ColorMode.OWNER
            )
        }

        with(outputIsolatedSubgraphSize.get().asFile.printWriter()) {
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

    private fun writeCoOccurrenceMatrix(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>,
        outputFile: File
    ) {
        val exporter = MatrixExporter<DependencyNode, DependencyEdge>(
            MatrixExporter.Format.SPARSE_ADJACENCY_MATRIX
        ) { v -> v.project }
        exporter.exportGraph(graph, outputFile)
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
                    "rebuiltTargetsByTransitiveDependencies"
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
                    it.rebuiltTargetsByTransitiveDependencies
                )
            }
        }.renderText().also {
            file.appendText(it)
            file.appendText("\n\n")
        }
    }

    private fun writeStatisticsCsv(
        graphStatistics: GraphStatistics,
        file: File
    ) {
        listOf(
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
        ).joinToString(",").let { line -> file.appendText(line + "\n") }

        graphStatistics.nodes.forEach {
            listOf(
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
            ).joinToString(",").let { line -> file.appendText(line + "\n") }
        }
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