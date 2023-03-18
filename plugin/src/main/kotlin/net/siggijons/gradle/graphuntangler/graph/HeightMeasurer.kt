package net.siggijons.gradle.graphuntangler.graph

import org.jgrapht.graph.DirectedAcyclicGraph

class HeightMeasurer<V, E>(
    private val graph: DirectedAcyclicGraph<V, E>
) {
    private val heightMap = LinkedHashMap<V, Int>()

    fun calculateHeightMap(): Map<V, Int> {
        graph.vertexSet().forEach { v ->
            heightOf(v)
        }
        return heightMap.toMap()
    }

    private fun heightOf(v: V): Int {
        val cached = heightMap[v]
        if (cached != null) {
            return cached
        }

        val descendants = graph.getDescendants(v)
        val height = if (descendants.isEmpty()) {
            0
        } else {
            descendants.maxOf { heightOf(it) } + 1
        }
        heightMap[v] = height
        return height
    }
}