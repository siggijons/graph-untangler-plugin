package net.siggijons.gradle.graphuntangler

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnersTest {

    @Test
    fun `given two module start with same name, should not override conflict in owner lookup`() {
        // Given
        val ownerMap = mapOf(
            ":app" to "Foo",
            ":app-catalog" to "Bar"
        )

        // When
        val appOwner = TODO("Lookup owner for :app")
        val appCatalogOwner = TODO("Lookup owner for :app-catalog")

        // Then
        assertEquals("Foo", appOwner)
        assertEquals("Bar", appCatalogOwner)
    }
}