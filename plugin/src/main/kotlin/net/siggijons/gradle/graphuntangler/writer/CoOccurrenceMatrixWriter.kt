package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.model.DependencyEdge
import net.siggijons.gradle.graphuntangler.model.DependencyNode
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.nio.matrix.MatrixExporter
import java.io.File

class CoOccurrenceMatrixWriter(
    private val outputFile: File
) {
    fun write(graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>) {
        val exporter = MatrixExporter<DependencyNode, DependencyEdge>(
            MatrixExporter.Format.SPARSE_ADJACENCY_MATRIX
        ) { v -> v.project }
        exporter.exportGraph(graph, outputFile)
    }
}