package net.siggijons.gradle.graphuntangler

import net.siggijons.gradle.graphuntangler.graph.DependencyEdge
import net.siggijons.gradle.graphuntangler.graph.DependencyNode
import net.siggijons.gradle.graphuntangler.owner.GitHubCodeOwnersReader
import net.siggijons.gradle.graphuntangler.owner.OwnerFileReader
import net.siggijons.gradle.graphuntangler.owner.Owners
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

    @get:Optional
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
    abstract val outputDotReducedOwners: RegularFileProperty

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

        val outputs = Outputs(
            projectsDir = outputProjectSubgraphs.get().asFile,
            statisticsOutput = output.get().asFile,
            statisticsCsvOutput = outputCsv.get().asFile,
            outputDot = outputDot.get().asFile,
            outputDotHeight = outputDotHeight.get().asFile,
            outputDotReduced = outputDotReduced.get().asFile,
            outputDotReducedOwners = outputDotReducedOwners.get().asFile,
            outputAdjacencyMatrix = outputAdjacencyMatrix.get().asFile,
            outputIsolatedSubgraphSize = outputIsolatedSubgraphSize.get().asFile
        )

        logger.quiet("Analyzing Graph")
        AnalyzeModuleGraph().run(graph, outputs)
    }

    private fun readOwners(): Owners {
        val file = ownersFile.orNull?.asFile ?: return Owners(ownerMap = emptyMap())
        return if (file.name == "CODEOWNERS") GitHubCodeOwnersReader().read(file)
        else OwnerFileReader().read(file)
    }

    private fun readFrequencyMap(): Map<String, Int> {
        val file = changeFrequencyFile.orNull?.asFile ?: return emptyMap()
        return file.readLines().drop(1).associate {
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