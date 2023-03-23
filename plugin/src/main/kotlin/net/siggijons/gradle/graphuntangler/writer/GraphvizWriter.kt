package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.color.ColorMode
import net.siggijons.gradle.graphuntangler.color.rateColor
import net.siggijons.gradle.graphuntangler.color.seriesColors
import net.siggijons.gradle.graphuntangler.graph.DependencyEdge
import net.siggijons.gradle.graphuntangler.graph.DependencyNode
import org.jgrapht.graph.AbstractGraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.File

class GraphvizWriter(
    private val labelStrategy: LabelStrategy = DefaultLabelStrategy()
) {

    fun writeDotGraph(
        graph: AbstractGraph<DependencyNode, DependencyEdge>,
        file: File,
        colorMode: ColorMode = ColorMode.CHANGE_RATE,
    ) {
        val exporter = DOTExporter<DependencyNode, DependencyEdge> { vertex ->
            vertex.project.replace("-", "_").replace(".", "_").replace(":", "_")
        }

        val colorMap = if (colorMode == ColorMode.OWNER) {
            val owners = graph.vertexSet()
                .groupingBy { it.owner.orEmpty() }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
                .map { it.first }
            seriesColors(owners)
        } else {
            emptyMap()
        }

        exporter.setVertexAttributeProvider { v ->
            val color = when (colorMode) {
                ColorMode.CHANGE_RATE -> v.normalizedChangeRate?.rateColor()
                ColorMode.OWNER -> colorMap[v.owner]
            }

            val label = labelStrategy.createLabel(v)

            mapOf(
                "label" to DefaultAttribute.createAttribute(label),
                "style" to DefaultAttribute.createAttribute("filled"),
                "fillcolor" to DefaultAttribute.createAttribute(color)
            )
        }

        file.delete()
        exporter.exportGraph(graph, file)
    }
}