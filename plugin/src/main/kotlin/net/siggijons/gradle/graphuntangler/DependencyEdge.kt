package net.siggijons.gradle.graphuntangler

import org.jgrapht.graph.DefaultEdge

class DependencyEdge(private val label: String) : DefaultEdge() {
    override fun toString(): String {
        return "($source : $target : $label)"
    }
}