package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.graph.DependencyNode

class DefaultLabelStrategy : LabelStrategy {
    override fun createLabel(node: DependencyNode): String {
        var label = node.changeRate?.let {
            "%s | %d".format(node.project, it)
        } ?: node.project

        if (node.owner != null) {
            label += "\\n${node.owner}"
        }
        return label
    }
}