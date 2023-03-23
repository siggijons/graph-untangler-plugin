package net.siggijons.gradle.graphuntangler.writer.label

import net.siggijons.gradle.graphuntangler.graph.DependencyNode

class EmptyLabelStrategy : LabelStrategy {
    override fun createLabel(node: DependencyNode): String = ""
}