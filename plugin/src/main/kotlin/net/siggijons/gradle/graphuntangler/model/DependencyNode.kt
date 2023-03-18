package net.siggijons.gradle.graphuntangler.model

data class DependencyNode(
    val project: String,
    val owner: String?,
    val changeRate: Int?,
    val normalizedChangeRate: Double?
)

val DependencyNode.safeFileName: String
    get() = project.replace(":", "_")