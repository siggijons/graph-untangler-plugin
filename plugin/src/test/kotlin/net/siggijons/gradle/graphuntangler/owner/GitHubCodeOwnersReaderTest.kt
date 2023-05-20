package net.siggijons.gradle.graphuntangler.owner

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubCodeOwnersReaderTest {

    private val reader = GitHubCodeOwnersReader()

    @Test
    fun `read - given empty CODEOWNERS, should return empty owners`() {
        val contents = ""
        val owners = reader.read(contents)

        assertEquals(emptyMap<String, String>(), owners.ownerMap)
    }

    @Test
    fun `read - given comments and empty lines, should ignore`() {
        val contents = """
            # foo bar
           
            # these are comments
        """.trimIndent()

        val owners = reader.read(contents)

        assertEquals(
            emptyMap<String, String>(),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given single owner, should read as owner`() {
        val contents = """/foo/bar @foo"""

        val owners = reader.read(contents)

        assertEquals(
            mapOf(":foo:bar" to "@foo"),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given trailing slash, should remove and map to module`() {
        val contents = """/foo/bar/ @foo"""

        val owners = reader.read(contents)

        assertEquals(
            mapOf(":foo:bar" to "@foo"),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given wildcard start, should ignore`() {
        val contents = """*.js @foo"""

        val owners = reader.read(contents)

        assertEquals(
            emptyMap<String, String>(),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given multiple owners, should use first`() {
        val contents = """/foo/bar @foo @bar @baz"""

        val owners = reader.read(contents)

        assertEquals(
            mapOf(":foo:bar" to "@foo"),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given no owner, should ignore and emit error`() {
        val contents = """/foo/bar"""

        val owners = reader.read(contents)

        assertEquals(
            emptyMap<String, String>(),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given trailing wildcard, should remove`() {
        val contents = """/foo/bar/baz/* @foo"""

        val owners = reader.read(contents)

        assertEquals(
            mapOf(":foo:bar:baz" to "@foo"),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given trailing wildcard with file extension, should remove`() {
        val contents = """/foo/bar/baz/*.xml @foo"""

        val owners = reader.read(contents)

        assertEquals(
            mapOf(":foo:bar:baz" to "@foo"),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given full file, should map to owners`() {
        val contents = """
            /app @App
            /feature/settings @App

            /feature/foryou @Feature
            /feature/interests @Feature
            /feature/bookmarks @Feature
            /feature/topic @Feature
            
            # Comments are supported 
            /core @Core
            /sync @Core

            /app-nia-catalog @Tools
            /benchmarks @Tools
            /lint @Tools
            /ui-test-hilt-manifest @Tools
        """.trimIndent()

        val owners = reader.read(contents)

        assertEquals(
            mapOf(
                ":app" to "@App",
                ":feature:settings" to "@App",
                ":feature:foryou" to "@Feature",
                ":feature:interests" to "@Feature",
                ":feature:bookmarks" to "@Feature",
                ":feature:topic" to "@Feature",
                ":core" to "@Core",
                ":sync" to "@Core",
                ":app-nia-catalog" to "@Tools",
                ":benchmarks" to "@Tools",
                ":lint" to "@Tools",
                ":ui-test-hilt-manifest" to "@Tools",
            ),
            owners.ownerMap
        )
    }

    @Test
    fun `read - given mixed spaces for alignment and comments, should read correctly`() {
        val contents = """
            /module-dir       @team-one  # no owner
            /other-module-dir @team-one  # single space, attributed properly
            /one-more.        @team-one  # no owner
        """.trimIndent()

        val owners = reader.read(contents)

        assertEquals(
            mapOf(
                ":module-dir" to "@team-one",
                ":other-module-dir" to "@team-one",
                ":one-more." to "@team-one" // not sure about that dot?
            ),
            owners.ownerMap
        )
    }
}