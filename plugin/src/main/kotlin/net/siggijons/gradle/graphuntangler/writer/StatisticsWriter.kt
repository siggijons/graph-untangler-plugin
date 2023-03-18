package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.graph.GraphStatistics

interface StatisticsWriter {
    fun write(graphStatistics: GraphStatistics)
}
