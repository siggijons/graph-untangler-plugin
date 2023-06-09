package net.siggijons.gradle.graphuntangler

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @suppress unused used as a plugin
 */
@Suppress("unused")
class GraphUntanglerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            GraphUntanglerPluginExtension::class.java,
            "untangler",
            GraphUntanglerPluginExtension::class.java
        )

        val buildDirectory = project.rootProject.layout.buildDirectory
        val changeFrequencyFile =
            buildDirectory.file("untangler/changeFrequency.csv")
        val analyzeModuleGraph =
            buildDirectory.file("untangler/analyzeModuleGraph.txt")
        val analyzeModuleGraphCsv =
            buildDirectory.file("untangler/analyzeModuleGraph.csv")
        val analyzeModuleGraphDot =
            buildDirectory.file("untangler/analyzeModuleGraph.gv")
        val analyzeModuleGraphDotHeight =
            buildDirectory.file("untangler/analyzeModuleGraph-height.gv")
        val analyzeModuleGraphDotReduced =
            buildDirectory.file("untangler/analyzeModuleGraph-reduced.gv")
        val analyzeModuleGraphDotReducedOwners =
            buildDirectory.file("untangler/analyzeModuleGraph-reduced-owners.gv")
        val analyzeModuleOutputAdjacencyMatrix =
            buildDirectory.file("untangler/analyzeModuleGraph-adjacencyMatrix.txt")
        val isolatedSubgraphSize =
            buildDirectory.file("untangler/isolated-subgraph-size.csv")
        val projectGraphs =
            buildDirectory.dir("untangler/projects")

        // Workaround for optional input file for owners.
        // Plugin should work with or without this data present.
        // This appears to be against task design guidelines
        val ownersFile = project.rootProject.layout.projectDirectory.file(extension.ownerFile)
            .let { file ->
                project.provider { if (file.asFile.exists()) file else null }
            }

        // Workaround for optional input file for change frequency.
        // If present. Can be used to calculate RTTD
        // When not available. Plugin continues to work and reports RTTD=0
        val optionalChangeFrequencyFile = project.provider {
            if (changeFrequencyFile.get().asFile.exists()) changeFrequencyFile.get()
            else null
        }

        project.tasks.register("analyzeModuleGraph", AnalyzeModuleGraphTask::class.java) { task ->
            task.configurationsToAnalyze.set(extension.configurationsToAnalyze)
            task.ownersFile.set(ownersFile)
            task.changeFrequencyFile.set(optionalChangeFrequencyFile)
            task.output.set(analyzeModuleGraph)
            task.outputCsv.set(analyzeModuleGraphCsv)
            task.outputDot.set(analyzeModuleGraphDot)
            task.outputDotHeight.set(analyzeModuleGraphDotHeight)
            task.outputDotReduced.set(analyzeModuleGraphDotReduced)
            task.outputDotReducedOwners.set(analyzeModuleGraphDotReducedOwners)
            task.outputAdjacencyMatrix.set(analyzeModuleOutputAdjacencyMatrix)
            task.outputIsolatedSubgraphSize.set(isolatedSubgraphSize)
            task.outputProjectSubgraphs.set(projectGraphs)
        }

        project.tasks.register(
            "generateChangeFrequencyFile",
            GenerateChangeFrequencyTask::class.java
        ) { task ->
            task.configurationsToAnalyze.set(extension.configurationsToAnalyze)
            task.output.set(changeFrequencyFile)
        }
    }
}