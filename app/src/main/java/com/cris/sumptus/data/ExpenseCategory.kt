package com.cris.sumptus.data

import java.util.Locale

fun normalizeCategoryName(rawValue: String): String {
    val locale = Locale.forLanguageTag("es-ES")
    return rawValue
        .trim()
        .replace(Regex("\\s+"), " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase(locale).replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase(locale)
                } else {
                    char.toString()
                }
            }
        }
}

fun legacyCategoryNameToLabel(rawValue: String): String = when (rawValue.trim()) {
    "HOGAR" -> "Hogar"
    "COMIDA" -> "Comida"
    "TRANSPORTE" -> "Transporte"
    "OCIO" -> "Ocio"
    "SALUD" -> "Salud"
    "SUSCRIPCIONES" -> "Suscripciones"
    "OTROS" -> "Otros"
    else -> normalizeCategoryName(rawValue).ifBlank { "Otros" }
}
