package com.cris.sumptus

import java.math.BigDecimal
import java.math.RoundingMode

fun parseAmountToCents(rawInput: String): Long? {
    val input = rawInput.trim().replace("€", "").replace(Regex("\\s+"), "")
    if (input.isBlank()) return null

    val comma = input.lastIndexOf(',')
    val dot = input.lastIndexOf('.')
    val normalized = when {
        comma >= 0 && dot >= 0 && comma > dot -> input.replace(".", "").replace(',', '.')
        comma >= 0 && dot >= 0 -> input.replace(",", "")
        comma >= 0 && input.length - comma - 1 == 3 -> input.replace(",", "")
        comma >= 0 -> input.replace(',', '.')
        dot >= 0 && input.length - dot - 1 == 3 -> input.replace(".", "")
        else -> input
    }

    return runCatching {
        normalized.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.multiply(BigDecimal(100))
            ?.longValueExact()
    }.getOrNull()
}
