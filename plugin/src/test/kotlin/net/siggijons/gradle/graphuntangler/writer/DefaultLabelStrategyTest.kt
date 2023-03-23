package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.graph.DependencyNode
import net.siggijons.gradle.graphuntangler.writer.label.DefaultLabelStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

internal class DefaultLabelStrategyTest {

    private val strategy = DefaultLabelStrategy()

    @Test
    fun `createLabel, given node with only project, should return project`() {
        // Given node
        val node = DependencyNode(
            project = "Project Name",
            owner = null,
            changeRate = null,
            normalizedChangeRate = null
        )

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals("Project Name", label)
    }

    @Test
    fun `createLabel, given node with change rate, should include change rate`() {
        // Given node
        val node = DependencyNode(
            project = "Project Name",
            owner = null,
            changeRate = 13,
            normalizedChangeRate = null
        )

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals("Project Name | 13", label)
    }

    @Test
    fun `createLabel, given node with owner, should include owner in new line`() {
        // Given node
        val node = DependencyNode(
            project = "Project Name",
            owner = "Owner",
            changeRate = 13,
            normalizedChangeRate = null
        )

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals("Project Name | 13\\nOwner", label)
    }

    @Test
    fun `createLabel, given node with owner, no change rate, should include project and owner`() {
        // Given node
        val node = DependencyNode(
            project = "Project Name",
            owner = "Owner",
            changeRate = null,
            normalizedChangeRate = null
        )

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals("Project Name\\nOwner", label)
    }
}