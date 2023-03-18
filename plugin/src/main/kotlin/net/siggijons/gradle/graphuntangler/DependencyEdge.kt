package net.siggijons.gradle.graphuntangler

import org.jgrapht.graph.DefaultEdge

class DependencyEdge(val label: String) : DefaultEdge() {
    override fun toString(): String {
        return "($source : $target : $label)"
    }
}

data class DependencyNode(
    val project: String,
    val owner: String?,
    val changeRate: Int?,
    val normalizedChangeRate: Double?
)