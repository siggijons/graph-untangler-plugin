package net.siggijons.gradle.graphuntangler

import net.siggijons.gradle.graphuntangler.model.DependencyNode

data class NodeStatistics(
    val node: DependencyNode,
    val betweennessCentrality: Double,
    val degree: Int,
    val inDegree: Int,
    val outDegree: Int,
    val height: Int,
    val ancestors: Int,
    val descendants: Int,
    val changeRate: Int,
    val descendantsChangeRate: Int,
    val ownershipInfo: OwnershipInfo?
) {
    /**
     * Rebuilt Targets by Transitive Dependencies estimates the actual impact a module has
     * on build performance by considering rate of change and number of transitive dependencies.
     *
     * https://opensourcelive.withgoogle.com/events/bazelcon2022?talk=day1-talk8
     * https://www.youtube.com/watch?v=k4H20WxhbsA
     */
    val rebuiltTargetsByTransitiveDependencies =
        (changeRate + descendantsChangeRate) * (ancestors + 1)


    data class OwnershipInfo(
        val nonSelfOwnedDescendants: Int,
        val uniqueNonSelfOwnedDescendants: Int,
        val nonSelfOwnedAncestors: Int,
        val uniqueNonSelfOwnedAncestors: Int
    )
}

data class GraphStatistics(
    val nodes: List<NodeStatistics>
)