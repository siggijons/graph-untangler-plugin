package net.siggijons.gradle.graphuntangler.graph

import org.jgrapht.graph.AbstractGraph
import org.jgrapht.graph.DirectedAcyclicGraph

data class SubgraphDetails(
    val vertex: DependencyNode,
    val subgraph: AbstractGraph<DependencyNode, DependencyEdge>,
    val descendants: Set<DependencyNode>,
    val subgraphHeightGraph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>
)