package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.graph.DependencyNode
import net.siggijons.gradle.graphuntangler.writer.label.EmptyLabelStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyLabelStrategyTest {

    private val strategy = EmptyLabelStrategy()

    @Test
    fun `createLabel, given any label`() {
        // Given
        val node = DependencyNode("Foo", null, null, null)

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals("", label)
    }
}