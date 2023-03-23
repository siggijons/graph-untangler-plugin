package net.siggijons.gradle.graphuntangler.writer.label

import net.siggijons.gradle.graphuntangler.graph.DependencyNode

interface LabelStrategy {
    fun createLabel(node: DependencyNode): String
}