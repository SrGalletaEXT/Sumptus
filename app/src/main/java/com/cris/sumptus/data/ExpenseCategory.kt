package com.cris.sumptus.data

enum class ExpenseCategory(val label: String) {
    HOGAR("Hogar"),
    COMIDA("Comida"),
    TRANSPORTE("Transporte"),
    OCIO("Ocio"),
    SALUD("Salud"),
    SUSCRIPCIONES("Suscripciones"),
    OTROS("Otros"),
    ;

    companion object {
        fun fromName(value: String): ExpenseCategory = entries.firstOrNull { it.name == value } ?: OTROS
    }
}
