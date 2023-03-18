package net.siggijons.gradle.graphuntangler.color

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ColorExtKtTest {

    @Test
    fun `seriesColors - given short list, should return map with entry for every list`() {
        // Given
        val inputs = (0..5).toList()

        // When
        val colors = seriesColors(inputs)

        // Then
        assertEquals(6, colors.size)
        assertEquals(colors[0], PALETTE[0])
        assertEquals(colors[5], PALETTE[5])
    }

    @Test
    fun `seriesColors - given long list, should return map filled with gray for more than 16`() {
        // Given
        val inputs = (0..20).toList()

        // When
        val colors = seriesColors(inputs)

        // Then
        assertEquals(21, colors.size)
        assertEquals(PALETTE[0], colors[0])
        assertEquals(PALETTE[15], colors[15])
        assertEquals(GRAY, colors[16])
        assertEquals(GRAY, colors[17])
        assertEquals(GRAY, colors[18])
        assertEquals(GRAY, colors[19])
        assertEquals(GRAY, colors[20])
    }
}