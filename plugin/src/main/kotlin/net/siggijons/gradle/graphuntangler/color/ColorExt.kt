package net.siggijons.gradle.graphuntangler.color

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

val PALETTE = arrayOf(
    "#e81e63CC", // Pink
    "#9c27b0CC", // Purple
    "#3f51b5CC", // Indigo
    "#2196f3CC", // Blue
    "#03a9f4CC", // Light Blue
    "#00bcd4CC", // Cyan
    "#009688CC", // Teal
    "#4caf50CC", // Green
    "#8bc34aCC", // Light Green
    "#cddc39CC", // Lime
    "#ffeb3bCC", // Yellow
    "#ffc107CC", // Amber
    "#ff9800CC", // Orange
    "#ff5722CC", // Deep Orange
    "#f44336CC", // Red
    "#673ab7CC", // Deep Purple
)

val GRAY = "#9e9e9e"

/**
 * Generate a color for a rate expressed as a double [0,1]
 * Interpolates between white and red using a cubic ease out
 */
fun Double?.rateColor(): String {
    val rate = this ?: 0.0
    val ease = 1 - (1 - rate).pow(3)
    val alpha = Integer.toHexString((255 * ease).roundToInt())
    return "#ff0000$alpha"
}

fun <T : Any> seriesColors(list: List<T>): Map<T, String> {
    val fillSize = max(0, list.size - PALETTE.size)
    val fillList = List(fillSize) { GRAY }
    return list.zip(PALETTE + fillList).toMap()
}