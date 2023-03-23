package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.graph.DependencyNode
import net.siggijons.gradle.graphuntangler.writer.label.AnonymizedLabelStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class AnonymizedLabelStrategyTest {

    private val strategy = AnonymizedLabelStrategy()

    @Test
    fun `createLabel, given app, should return app`() {
        // Given
        val node = node(":app")

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals(":app", label)
    }

    @Test
    fun `createLabel, given app foo, should hide foo`() {
        // Given
        val node = node(":app:foo")

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals(":app:*", label)
    }

    @Test
    fun `createLabel, given secret public library, should hide secret`() {
        // Given
        val node = node(":library:secret:public")

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals(":library:*:public", label)
    }

    @Test
    fun `createLabel, given secret feature, should hide secret`() {
        // Given
        val node = node(":feature:secret")

        // When
        val label = strategy.createLabel(node)

        // Then
        assertEquals(":feature:*", label)
    }

    private fun node(project: String): DependencyNode {
        return DependencyNode(
            project = project,
            owner = null,
            changeRate = null,
            normalizedChangeRate = null
        )
    }
}