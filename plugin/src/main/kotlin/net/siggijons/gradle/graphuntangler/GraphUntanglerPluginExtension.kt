package net.siggijons.gradle.graphuntangler

open class GraphUntanglerPluginExtension {
    var configurationsToAnalyze = setOf("api", "implementation")
    var ownerFile: String = "owners.yaml"
}