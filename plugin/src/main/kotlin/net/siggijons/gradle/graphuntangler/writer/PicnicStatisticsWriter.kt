package net.siggijons.gradle.graphuntangler.writer

import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table
import net.siggijons.gradle.graphuntangler.GraphStatistics
import java.io.File

class PicnicStatisticsWriter(
    private val file: File
): StatisticsWriter {
    override fun write(graphStatistics: GraphStatistics) {
        table {
            cellStyle {
                paddingLeft = 1
                paddingRight = 1
            }
            header {
                row(
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
                    "rebuiltTargetsByTransitiveDependencies"
                )
            }
            graphStatistics.nodes.forEach {
                row(
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
                    it.rebuiltTargetsByTransitiveDependencies
                )
            }
        }.renderText().also {
            file.appendText(it)
            file.appendText("\n\n")
        }
    }
}