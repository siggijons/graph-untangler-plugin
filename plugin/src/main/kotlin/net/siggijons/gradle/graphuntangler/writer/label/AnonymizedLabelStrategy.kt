package net.siggijons.gradle.graphuntangler.writer.label

import net.siggijons.gradle.graphuntangler.graph.DependencyNode

val DEFAULT = setOf("app", "feature", "library", "core", "common", "public", "internal")

class AnonymizedLabelStrategy(
    private val allowedLabels: Set<String> = DEFAULT
) : LabelStrategy {
    override fun createLabel(node: DependencyNode): String {
        return node.project.split(":").joinToString(":") {
            if (allowedLabels.contains(it) || it.isEmpty()) it else "*"
        }
    }
}