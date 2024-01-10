package me.rafaelldi.aspire.diagram

internal fun Int.weightToWidth() = when (this) {
    in 1..2 -> 2
    in 3..5 -> 3
    in 6..8 -> 4
    in 9..15 -> 5
    else -> 6
}