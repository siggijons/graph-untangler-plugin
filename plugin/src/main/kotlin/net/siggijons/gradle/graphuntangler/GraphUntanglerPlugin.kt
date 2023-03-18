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
            buildDirectory.file("untangler/analyzeModuleGraph.dot")
        val analyzeModuleGraphDotHeight =
            buildDirectory.file("untangler/analyzeModuleGraph-height.dot")
        val analyzeModuleGraphDotReduced =
            buildDirectory.file("untangler/analyzeModuleGraph-reduced.dot")
        val analyzeModuleOutputAdjacencyMatrix =
            buildDirectory.file("untangler/analyzeModuleGraph-adjacencyMatrix.txt")
        val isolatedSubgraphSize =
            buildDirectory.file("untangler/isolated-subgraph-size.csv")
        val projectGraphs =
            buildDirectory.dir("untangler/projects")

        val ownersFile = project.rootProject.layout.projectDirectory.file(extension.ownerFile)
            .let { file ->
                project.provider { if (file.asFile.exists()) file else null }
            }

        project.tasks.register("analyzeModuleGraph", AnalyzeModuleGraphTask::class.java) { task ->
            task.configurationsToAnalyze.set(extension.configurationsToAnalyze)
            task.ownersFile.set(ownersFile)
            task.changeFrequencyFile.set(changeFrequencyFile)
            task.output.set(analyzeModuleGraph)
            task.outputCsv.set(analyzeModuleGraphCsv)
            task.outputDot.set(analyzeModuleGraphDot)
            task.outputDotHeight.set(analyzeModuleGraphDotHeight)
            task.outputDotReduced.set(analyzeModuleGraphDotReduced)
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