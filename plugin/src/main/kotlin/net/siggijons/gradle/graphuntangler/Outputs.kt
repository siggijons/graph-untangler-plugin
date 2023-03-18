package net.siggijons.gradle.graphuntangler

import java.io.File

data class Outputs(
    val projectsDir: File,
    val statisticsOutput: File,
    val statisticsCsvOutput: File,
    val outputDot: File,
    val outputDotHeight: File,
    val outputDotReduced: File,
    val outputAdjacencyMatrix: File,
    val outputIsolatedSubgraphSize: File
)