package com.cris.sumptus

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val expenseInputDateFormatter = DateTimeFormatter
    .ofPattern("dd/MM/uuuu")
    .withResolverStyle(ResolverStyle.STRICT)

fun formatExpenseInputDate(date: LocalDate): String = expenseInputDateFormatter.format(date)

fun parseExpenseInputDate(rawInput: String): LocalDate? = runCatching {
    LocalDate.parse(rawInput.trim(), expenseInputDateFormatter)
}.getOrNull()
