package com.cris.sumptus.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONObject

enum class IncomeType {
    SALARY,
    EXTRA,
}

data class ExtraEntry(
    val id: String = UUID.randomUUID().toString(),
    val amountCents: Long,
    val notes: String,
    val occurredOn: LocalDate,
    val type: IncomeType = IncomeType.EXTRA,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("amountCents", amountCents)
        .put("notes", notes)
        .put("occurredOn", occurredOn.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .put("type", type.name)
        .put("createdAtEpochMillis", createdAtEpochMillis)

    companion object {
        fun fromJson(json: JSONObject): ExtraEntry? = runCatching {
            ExtraEntry(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                amountCents = json.getLong("amountCents"),
                notes = json.optString("notes"),
                occurredOn = LocalDate.parse(json.getString("occurredOn")),
                type = enumValueOfOrNull(json.optString("type")) ?: IncomeType.EXTRA,
                createdAtEpochMillis = json.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()

        private fun enumValueOfOrNull(value: String): IncomeType? = runCatching {
            IncomeType.valueOf(value)
        }.getOrNull()
    }
}
