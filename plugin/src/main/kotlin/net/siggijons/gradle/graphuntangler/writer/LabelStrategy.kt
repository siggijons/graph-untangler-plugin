package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.graph.DependencyNode

interface LabelStrategy {
    fun createLabel(node: DependencyNode): String
}