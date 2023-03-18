package net.siggijons.gradle.graphuntangler.model

import net.siggijons.gradle.graphuntangler.GraphStatistics
import net.siggijons.gradle.graphuntangler.NodeStatistics
import org.jgrapht.GraphMetrics
import org.jgrapht.alg.TransitiveReduction
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.graph.AbstractGraph
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.traverse.TopologicalOrderIterator

class GraphUntangler {

    /**
     * Calculate statistics for graph.
     */
    fun nodeStatistics(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>
    ): GraphStatistics {
        val betweennessCentrality = BetweennessCentrality(graph).scores
        val heights = heights(graph)
        val iterator = TopologicalOrderIterator(graph)
        val nodes = mutableListOf<NodeStatistics>()
        while (iterator.hasNext()) {
            val node = iterator.next()
            val descendants = graph.getDescendants(node)
            val ancestors = graph.getAncestors(node)
            val descendantsChangeRate = descendants.sumOf { it.changeRate ?: 0 }

            val ownershipInfo = NodeStatistics.OwnershipInfo(
                nonSelfOwnedDescendants = descendants.count { it.owner != node.owner },
                uniqueNonSelfOwnedDescendants = descendants
                    .filter { it.owner != node.owner }
                    .distinctBy { it.owner }
                    .count(),
                nonSelfOwnedAncestors = ancestors.count { it.owner != node.owner },
                uniqueNonSelfOwnedAncestors = ancestors
                    .filter { it.owner != node.owner }
                    .distinctBy { it.owner }
                    .count()
            )

            val s = NodeStatistics(
                node = node,
                betweennessCentrality = requireNotNull(betweennessCentrality[node]) {
                    "betweennessCentrality not found for $node"
                },
                degree = graph.degreeOf(node),
                inDegree = graph.inDegreeOf(node),
                outDegree = graph.outDegreeOf(node),
                height = heights.heightMap[node] ?: -1,
                ancestors = ancestors.size,
                descendants = descendants.size,
                changeRate = node.changeRate ?: 0,
                descendantsChangeRate = descendantsChangeRate,
                ownershipInfo = ownershipInfo
            )
            nodes.add(s)
        }

        return GraphStatistics(
            nodes = nodes
        )
    }

    /**
     * Generate a graph that consists only of nodes that participate in the longest paths
     * across the graph. This can be useful when there are multiple longest paths in a graph.
     * The algorithm is naive and unproven.
     */
    fun heightGraph(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>,
        nodes: List<NodeStatistics>
    ): DirectedAcyclicGraph<DependencyNode, DependencyEdge> {
        val g = DirectedAcyclicGraph<DependencyNode, DependencyEdge>(DependencyEdge::class.java)

        val added = mutableSetOf<DependencyNode>()
        val byHeight = nodes.sortedByDescending { it.height }.groupBy { it.height }
        byHeight.forEach { (_, currentLevel) ->

            if (added.isEmpty()) {
                currentLevel.forEach {
                    g.addVertex(it.node)
                    added.add(it.node)
                }
            } else {
                val connectionsToPrevious = currentLevel.map { v ->
                    v.node to added.filter { u -> graph.containsEdge(u, v.node) }
                }.filter { it.second.isNotEmpty() }

                added.clear()
                connectionsToPrevious.forEach { (u, vs) ->
                    vs.forEach {
                        g.addVertex(u)
                        g.addEdge(it, u, DependencyEdge(label = "Height Neighbor"))
                        added.add(u)
                    }
                }
            }
        }
        return g
    }

    /**
     * Creates a Graph and [heightGraph] for each vertex in [graph]
     * @see [AsSubgraph]
     */
    fun analyzeSubgraphs(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>
    ): List<SubgraphDetails> {
        return graph.vertexSet().map { vertex ->
            val descendants = graph.getDescendants(vertex)
            val subgraph = AsSubgraph(graph, descendants + vertex)

            val dag = DirectedAcyclicGraph.createBuilder<DependencyNode, DependencyEdge>(
                DependencyEdge::class.java
            ).addGraph(subgraph).build()

            val dagStats = nodeStatistics(dag)
            val subgraphHeightGraph = heightGraph(dag, dagStats.nodes)

            SubgraphDetails(
                vertex = vertex,
                subgraph = subgraph,
                descendants = descendants,
                subgraphHeightGraph = subgraphHeightGraph
            )
        }
    }

    /**
     * Creates a subgraph for every module, or vertex, in the graph that only includes
     * vertices that are either ancestors or descendants of the vertex, as well as the vertex
     * itself.
     *
     * This creates a representation that makes it possible to reason how a specific module
     * interacts with the rest of the graph and can help visualize the value captured by RTTD.
     *
     * Additionally a csv, isolated-subgraph-size.csv, is created capturing the size of each
     * subgraph for further analysis.
     *
     * @see [NodeStatistics.rebuiltTargetsByTransitiveDependencies]
     */
    fun isolateSubgraphs(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>
    ): List<IsolatedSubgraphDetails> {
        return graph.vertexSet().map { vertex ->
            val ancestors = graph.getAncestors(vertex)
            val descendants = graph.getDescendants(vertex)

            val builder = DirectedAcyclicGraph.createBuilder<DependencyNode, DependencyEdge>(
                DependencyEdge::class.java
            )
            builder.addGraph(graph)
            val disconnected = graph.vertexSet() - ancestors - descendants - vertex
            disconnected.forEach {
                builder.removeVertex(it)
            }
            val isolatedDag = builder.build()

            @Suppress("UNCHECKED_CAST")
            val reducedDag = isolatedDag.clone() as AbstractGraph<DependencyNode, DependencyEdge>
            TransitiveReduction.INSTANCE.reduce(reducedDag)

            IsolatedSubgraphDetails(
                vertex = vertex,
                isolatedDag = isolatedDag,
                reducedDag = reducedDag,
                isolatedDagSize = isolatedDag.vertexSet().size,
                fullGraphSize = graph.vertexSet().size
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun safeReduce(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>
    ): AbstractGraph<DependencyNode, DependencyEdge> {
        val clone = graph.clone() as AbstractGraph<DependencyNode, DependencyEdge>
        TransitiveReduction.INSTANCE.reduce(clone)
        return clone
    }

    /**
     * Calculate the "height" of the dependency graph.
     *
     * This was thought to be equal to the graph diameter, but for some reasons the diameter
     * as calculated by [GraphMetrics.getDiameter] has tended to return 0 for dags.
     */
    private fun heights(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>
    ): Heights<DependencyNode> {
        val map = HeightMeasurer(graph = graph).calculateHeightMap()
        return Heights(map)
    }
}