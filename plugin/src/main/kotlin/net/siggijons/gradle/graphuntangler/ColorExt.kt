package net.siggijons.gradle.graphuntangler

import kotlin.math.pow
import kotlin.math.roundToInt

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