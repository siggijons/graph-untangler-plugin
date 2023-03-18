package net.siggijons.gradle.graphuntangler

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertNotNull
import org.junit.Test


class GraphUntanglerPluginTest {

    @Test
    fun pluginAddsTaskToProject() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("net.siggijons.gradle.graphuntangler")

        // When
        val task = project.tasks.getByName("createDAG")

        // Then
        assertNotNull(task)
    }

}