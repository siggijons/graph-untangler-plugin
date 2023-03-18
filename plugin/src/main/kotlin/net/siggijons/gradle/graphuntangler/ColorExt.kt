package net.siggijons.gradle.graphuntangler

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt


val PALLETTE = arrayOf(
    "#f44336", // Red
    "#e81e63", // Pink
    "#9c27b0", // Purple
    "#673ab7", // Deep Purple
    "#3f51b5", // Indigo
    "#2196f3", // Blue
    "#03a9f4", // Light Blue
    "#00bcd4", // Cyan
    "#009688", // Teal
    "#4caf50", // Green
    "#8bc34a", // Light Green
    "#cddc39", // Lime
    "#ffeb3b", // Yellow
    "#ffc107", // Amber
    "#ff9800", // Orange
    "#ff5722", // Deep Orange
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
    val fillSize = max(0, list.size - PALLETTE.size)
    val fillList = List(fillSize) { GRAY }
    return list.zip(PALLETTE + fillList).toMap()
}