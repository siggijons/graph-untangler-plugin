package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.color.ColorMode
import net.siggijons.gradle.graphuntangler.graph.DependencyEdge
import net.siggijons.gradle.graphuntangler.graph.DependencyNode
import org.jgrapht.graph.DirectedAcyclicGraph
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class GraphvizWriterTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val graphvizWriter = GraphvizWriter()

    @Test
    fun `writeDotGraph - given change rate not set, fill color should not be null`() {
        // Given
        val graph = DirectedAcyclicGraph<DependencyNode, DependencyEdge>(
            DependencyEdge::class.java
        )
        graph.addVertex(
            DependencyNode(
                project = "test",
                owner = null,
                changeRate = null,
                normalizedChangeRate = null
            )
        )

        val file = folder.newFile()

        // When
        graphvizWriter.writeDotGraph(
            colorMode = ColorMode.CHANGE_RATE,
            graph = graph,
            file = file
        )

        // Then
        assertEquals(
            """
            strict digraph G {
              test [ label="test" ];
            }
            
            """.trimIndent(),
            file.readText()
        )
    }

    @Test
    fun `writeDotGraph - given change rate is 1, fill color should be red`() {
        // Given
        val graph = DirectedAcyclicGraph<DependencyNode, DependencyEdge>(
            DependencyEdge::class.java
        )
        graph.addVertex(
            DependencyNode(
                project = "test",
                owner = null,
                changeRate = null,
                normalizedChangeRate = 1.0
            )
        )

        val file = folder.newFile()

        // When
        graphvizWriter.writeDotGraph(
            colorMode = ColorMode.CHANGE_RATE,
            graph = graph,
            file = file
        )

        // Then
        assertEquals(
            """
            strict digraph G {
              test [ label="test" style="filled" fillcolor="#ff0000ff" ];
            }
            
            """.trimIndent(),
            file.readText()
        )
    }
}