package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.model.DependencyNode

val DependencyNode.safeFileName: String
    get() = project.replace(":", "_")