package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.GraphStatistics

interface StatisticsWriter {
    fun write(graphStatistics: GraphStatistics)
}
