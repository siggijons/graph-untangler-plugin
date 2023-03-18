package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.GraphStatistics
import java.io.File

class CSVStatisticsWriter(
    private val file: File
) : StatisticsWriter {
    override fun write(graphStatistics: GraphStatistics) {
        listOf(
            "node",
            "owner",
            "betweennessCentrality",
            "degree",
            "inDegree",
            "outDegree",
            "height",
            "ancestors",
            "descendants",
            "changeRate",
            "descendantsChangeRate",
            "rebuiltTargetsByTransitiveDependencies",
            "nonSelfOwnedDescendants",
            "uniqueNonSelfOwnedDescendants",
            "nonSelfOwnedAncestors",
            "uniqueNonSelfOwnedAncestors"
        ).joinToString(",").let { line -> file.appendText(line + "\n") }

        graphStatistics.nodes.forEach {
            listOf(
                it.node.project,
                it.node.owner,
                "%.2f".format(it.betweennessCentrality),
                it.degree,
                it.inDegree,
                it.outDegree,
                it.height,
                it.ancestors,
                it.descendants,
                it.changeRate,
                it.descendantsChangeRate,
                it.rebuiltTargetsByTransitiveDependencies,
                it.ownershipInfo?.nonSelfOwnedDescendants,
                it.ownershipInfo?.uniqueNonSelfOwnedDescendants,
                it.ownershipInfo?.nonSelfOwnedAncestors,
                it.ownershipInfo?.uniqueNonSelfOwnedAncestors
            ).joinToString(",").let { line -> file.appendText(line + "\n") }
        }
    }
}