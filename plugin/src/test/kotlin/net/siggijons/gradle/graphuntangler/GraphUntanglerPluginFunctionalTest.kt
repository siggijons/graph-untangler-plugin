package net.siggijons.gradle.graphuntangler

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File


class GraphUntanglerPluginFunctionalTest {

    @get:Rule
    val temp = TemporaryFolder()
    private lateinit var testProjectFile: File
    private lateinit var settingsFile: File
    private lateinit var buildFile: File

    @Before
    fun setup() {
        testProjectFile = temp.newFolder()
        settingsFile = File(testProjectFile, "settings.gradle")
        buildFile = File(testProjectFile, "build.gradle")
    }

    @Test
    fun testHelloWorldTask() {
        // Given
        settingsFile.writeText("rootProject.name = 'hello-world'")
        buildFile.writeText(
            """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        """.trimIndent()
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectFile)
            .withArguments(":helloWorld")
            .build()

        // Then
        assertTrue(result.output.contains("Hello world!"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld")?.outcome)
    }

    @Ignore("Cant find the plugin")
    @Test
    fun testPlugin() {
        // Given
        settingsFile.writeText("rootProject.name = 'hello-world'")
        buildFile.writeText(
            """
            plugins {
              id('net.siggijons.gradle.graphuntangler') version "0.0.1" 
            }
        """.trimIndent()
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectFile)
            .withArguments(":analyzeModuleGraph")
            .build()

        // Then
        assertTrue(result.output.contains("Hello from the GraphUntanglerPlugin!"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":analyzeModuleGraph")?.outcome)
    }

}