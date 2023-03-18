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
        val analyzeModuleGraph =
            buildDirectory.file("untangler/analyzeModuleGraph.txt")
        val analyzeModuleGraphDot =
            buildDirectory.file("untangler/analyzeModuleGraph.dot")
        val analyzeModuleGraphDotDepth =
            buildDirectory.file("untangler/analyzeModuleGraph-depth.dot")
        val analyzeModuleGraphDotReduced =
            buildDirectory.file("untangler/analyzeModuleGraph-reduced.dot")

        project.tasks.register("analyzeModuleGraph", AnalyzeModuleGraphTask::class.java) { task ->
            task.configurationsToAnalyze.set(extension.configurationsToAnalyze)
            task.rootNode.set(extension.rootNode)
            task.output.set(analyzeModuleGraph)
            task.outputDot.set(analyzeModuleGraphDot)
            task.outputDotDepth.set(analyzeModuleGraphDotDepth)
            task.outputDotReduced.set(analyzeModuleGraphDotReduced)
        }
    }
}
