package net.siggijons.gradle.graphuntangler.graph

import org.jgrapht.graph.DefaultEdge

class DependencyEdge(val label: String) : DefaultEdge() {
    override fun toString(): String {
        return "($source : $target : $label)"
    }
}