package net.siggijons.gradle.graphuntangler

import net.siggijons.gradle.graphuntangler.color.ColorMode
import net.siggijons.gradle.graphuntangler.graph.DependencyEdge
import net.siggijons.gradle.graphuntangler.graph.DependencyNode
import net.siggijons.gradle.graphuntangler.graph.GraphUntangler
import net.siggijons.gradle.graphuntangler.writer.CSVStatisticsWriter
import net.siggijons.gradle.graphuntangler.writer.CoOccurrenceMatrixWriter
import net.siggijons.gradle.graphuntangler.writer.GraphvizWriter
import net.siggijons.gradle.graphuntangler.writer.PicnicStatisticsWriter
import net.siggijons.gradle.graphuntangler.writer.SubgraphSizeWriter
import net.siggijons.gradle.graphuntangler.writer.SubgraphWriter
import net.siggijons.gradle.graphuntangler.writer.SubgraphsDependantsWriter
import org.jgrapht.graph.DirectedAcyclicGraph

class AnalyzeModuleGraph {

    fun run(
        graph: DirectedAcyclicGraph<DependencyNode, DependencyEdge>,
        outputs: Outputs
    ) {
        // Calculate Statistics
        val graphUntangler = GraphUntangler()
        val nodeStatistics = graphUntangler.nodeStatistics(graph)
        val reducedGraph = graphUntangler.safeReduce(graph)
        val heightGraph = graphUntangler.heightGraph(graph, nodeStatistics.nodes)
        val subgraphs = graphUntangler.analyzeSubgraphs(graph)
        val isolatedSubgraphs = graphUntangler.isolateSubgraphs(graph)

        // Clean
        outputs.projectsDir.deleteRecursively()
        outputs.projectsDir.mkdirs()

        outputs.statisticsOutput.delete()
        outputs.statisticsCsvOutput.delete()

        // Write Stats
        PicnicStatisticsWriter(outputs.statisticsOutput).write(nodeStatistics)
        CSVStatisticsWriter(outputs.statisticsCsvOutput).write(nodeStatistics)

        val graphvizWriter = GraphvizWriter()
        graphvizWriter.writeDotGraph(graph, outputs.outputDot)
        graphvizWriter.writeDotGraph(heightGraph, outputs.outputDotHeight)
        graphvizWriter.writeDotGraph(reducedGraph, outputs.outputDotReduced)
        graphvizWriter.writeDotGraph(
            reducedGraph,
            outputs.outputDotReducedOwners,
            colorMode = ColorMode.OWNER
        )

        CoOccurrenceMatrixWriter(outputs.outputAdjacencyMatrix).write(graph)
        SubgraphSizeWriter(outputs.outputIsolatedSubgraphSize).write(isolatedSubgraphs)
        SubgraphsDependantsWriter(outputs.projectsDir).write(subgraphs)
        SubgraphWriter(outputs.projectsDir).write(subgraphs, isolatedSubgraphs)
    }
}