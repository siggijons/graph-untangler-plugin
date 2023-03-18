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

        val analyzeModuleGraph =
            project.rootProject.layout.buildDirectory.file("untangler/analyzeModuleGraph.txt")
        val analyzeModuleGraphDot =
            project.rootProject.layout.buildDirectory.file("untangler/analyzeModuleGraph.dot")

        project.tasks.register("analyzeModuleGraph", AnalyzeModuleGraphTask::class.java) { task ->
            task.configurationsToAnalyze.set(extension.configurationsToAnalyze)
            task.output.set(analyzeModuleGraph)
            task.outputDot.set(analyzeModuleGraphDot)
        }
    }
}
