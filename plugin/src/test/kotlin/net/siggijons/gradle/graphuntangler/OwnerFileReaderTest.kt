package net.siggijons.gradle.graphuntangler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OwnerFileReaderTest {

    private val ownerFileReader = OwnerFileReader()

    @Test
    fun `read - given invalid owners file`() {
        val contents = """ not yaml """
        val result = kotlin.runCatching {
            ownerFileReader.read(contents)
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `read - given valid owners file, should return map`() {
        val contents = """
            foo:
              team: foo-team
              modules: [module-one, module-two]
            bar:
              team: bar-team
              modules: [bar-module]
            baz:
              team: baz-team
              modules: []
        """.trimIndent()

        val ownerMap = ownerFileReader.read(contents)

        assertEquals(
            mapOf(
                "module-one" to "foo-team",
                "module-two" to "foo-team",
                "bar-module" to "bar-team",
            ), ownerMap
        )
    }
}