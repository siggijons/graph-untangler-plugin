package net.siggijons.gradle.graphuntangler.graph

data class DependencyNode(
    val project: String,
    val owner: String?,
    val changeRate: Int?,
    val normalizedChangeRate: Double?
)
