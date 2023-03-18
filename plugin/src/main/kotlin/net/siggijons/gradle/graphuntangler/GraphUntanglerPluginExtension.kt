package net.siggijons.gradle.graphuntangler

open class GraphUntanglerPluginExtension {
    var message: String = "Hello World!"

    var configurationsToAnalyze = setOf("api", "implementation")

    var rootNode: String? = null

    var ownerFile: String? = null
}