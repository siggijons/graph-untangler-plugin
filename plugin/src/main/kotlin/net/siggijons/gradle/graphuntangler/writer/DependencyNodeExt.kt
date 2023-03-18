package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.graph.DependencyNode

val DependencyNode.safeFileName: String
    get() = project.replace(":", "_")