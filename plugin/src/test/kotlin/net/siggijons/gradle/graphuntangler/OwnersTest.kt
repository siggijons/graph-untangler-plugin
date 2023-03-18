package net.siggijons.gradle.graphuntangler

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnersTest {

    @Test
    fun `given two module start with same name, should not override conflict in owner lookup`() {
        // Given
        val owners = Owners(
            ownerMap = mapOf(
                ":app" to "Foo",
                ":app-catalog" to "Bar"
            )
        )

        // When
        val appOwner = owners.findOwner(":app")
        val appCatalogOwner = owners.findOwner(":app-catalog")

        // Then
        assertEquals("Foo", appOwner)
        assertEquals("Bar", appCatalogOwner)
    }

    @Test
    fun `findOwner, given matches start, should return match`() {
        // Given
        val owners = Owners(
            ownerMap = mapOf(
                ":foo" to "Owner"
            )
        )

        // When
        val fooOwner = owners.findOwner(":foo")
        val barOwner = owners.findOwner(":foo:bar")
        val bazOwner = owners.findOwner(":foo:bar:baz")

        // Then
        assertEquals("Owner", fooOwner)
        assertEquals("Owner", barOwner)
        assertEquals("Owner", bazOwner)
    }
}