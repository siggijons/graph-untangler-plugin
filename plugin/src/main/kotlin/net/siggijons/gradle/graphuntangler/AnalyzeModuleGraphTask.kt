package net.siggijons.gradle.graphuntangler

import net.siggijons.gradle.graphuntangler.model.DependencyEdge
import net.siggijons.gradle.graphuntangler.model.DependencyNode
import net.siggijons.gradle.graphuntangler.model.GraphUntangler
import net.siggijons.gradle.graphuntangler.writer.CSVStatisticsWriter
import net.siggijons.gradle.graphuntangler.writer.CoOccurrenceMatrixWriter
import net.siggijons.gradle.graphuntangler.writer.GraphvizWriter
import net.siggijons.gradle.graphuntangler.writer.PicnicStatisticsWriter
import net.siggijons.gradle.graphuntangler.writer.SubgraphSizeWriter
import net.siggijons.gradle.graphuntangler.writer.SubgraphWriter
import net.siggijons.gradle.graphuntangler.writer.SubgraphsDependantsWriter
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

        PicnicStatisticsWriter(statisticsOutput).write(nodeStatistics)
        CSVStatisticsWriter(statisticsCsvOutput).write(nodeStatistics)

        val graphvizWriter = GraphvizWriter()
        graphvizWriter.writeDotGraph(graph, outputDot.get().asFile)
        graphvizWriter.writeDotGraph(heightGraph, outputDotHeight.get().asFile)
        graphvizWriter.writeDotGraph(reducedGraph, outputDotReduced.get().asFile)

        CoOccurrenceMatrixWriter(outputAdjacencyMatrix.get().asFile).write(graph)
        SubgraphSizeWriter(outputIsolatedSubgraphSize.get().asFile).write(isolatedSubgraphs)
        SubgraphsDependantsWriter(projectsDir).write(subgraphs)
        SubgraphWriter(projectsDir).write(subgraphs, isolatedSubgraphs)
    }

    private fun readOwners(): Owners {
        val file = ownersFile.orNull ?: return Owners(ownerMap = emptyMap())
        return OwnerFileReader().read(file.asFile)
    }

    private fun readFrequencyMap(): Map<String, Int> {
        return changeFrequencyFile.get().asFile.readLines().drop(1).associate {
            val s = it.split(",")
            s[0] to s[1].toInt()
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