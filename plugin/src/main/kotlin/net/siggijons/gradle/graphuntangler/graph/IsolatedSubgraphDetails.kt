package net.siggijons.gradle.graphuntangler.graph

import org.jgrapht.graph.AbstractGraph

data class IsolatedSubgraphDetails(
    val vertex: DependencyNode,
    val isolatedDag: AbstractGraph<DependencyNode, DependencyEdge>,
    val reducedDag: AbstractGraph<DependencyNode, DependencyEdge>,
    val isolatedDagSize: Int,
    val fullGraphSize: Int
)