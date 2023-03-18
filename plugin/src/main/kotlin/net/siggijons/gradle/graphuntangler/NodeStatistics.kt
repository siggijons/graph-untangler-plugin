package net.siggijons.gradle.graphuntangler

data class NodeStatistics(
    val node: String,
    val betweennessCentrality: Double,
    val degree: Int,
    val inDegree: Int,
    val outDegree: Int,
    val height: Int,
    val ancestors: Int,
    val descendants: Int,
    val changeRate: Int,
    val descendantsChangeRate: Int
) {
    // TODO: use a better name. "Rebuilt Targets by Transitive Dependencies" feels excessive
    val spotifyBadness = (changeRate + descendantsChangeRate) * (ancestors + 1)
}

data class GraphStatistics(
    val nodes: List<NodeStatistics>
)