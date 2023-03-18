package net.siggijons.gradle.graphuntangler

data class NodeStatistics(
    val node: String,
    val betweennessCentrality: Double,
    val degree: Int,
    val inDegree: Int,
    val outDegree: Int,
    val depth: Int,
    val eccentricity: Double
)

data class GraphStatistics(
    val nodes: List<NodeStatistics>
)