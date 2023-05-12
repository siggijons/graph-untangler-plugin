package net.siggijons.gradle.graphuntangler

open class GraphUntanglerPluginExtension {
    var configurationsToAnalyze = setOf("api", "implementation")
    // TODO: breaking changes, make this an override or alt config instead of owners.yaml
    var ownerFile: String = ".github/CODEOWNERS"
}