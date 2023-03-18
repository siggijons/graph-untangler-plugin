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
            buildDirectory.file("untangler/changeFrequency.txt")
        val analyzeModuleGraph =
            buildDirectory.file("untangler/analyzeModuleGraph.txt")
        val analyzeModuleGraphDot =
            buildDirectory.file("untangler/analyzeModuleGraph.dot")
        val analyzeModuleGraphDotHeight =
            buildDirectory.file("untangler/analyzeModuleGraph-height.dot")
        val analyzeModuleGraphDotReduced =
            buildDirectory.file("untangler/analyzeModuleGraph-reduced.dot")
        val analyzeModuleOutputAdjacencyMatrix =
            buildDirectory.file("untangler/analyzeModuleGraph-adjacencyMatrix.txt")

        project.tasks.register("analyzeModuleGraph", AnalyzeModuleGraphTask::class.java) { task ->
            task.configurationsToAnalyze.set(extension.configurationsToAnalyze)
            task.rootNode.set(extension.rootNode)
            task.changeFrequencyFile.set(changeFrequencyFile)
            task.output.set(analyzeModuleGraph)
            task.outputDot.set(analyzeModuleGraphDot)
            task.outputDotHeight.set(analyzeModuleGraphDotHeight)
            task.outputDotReduced.set(analyzeModuleGraphDotReduced)
            task.outputAdjacencyMatrix.set(analyzeModuleOutputAdjacencyMatrix)
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