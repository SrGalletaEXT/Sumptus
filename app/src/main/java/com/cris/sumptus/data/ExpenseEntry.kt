package com.cris.sumptus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONObject

@Entity(tableName = "expenses")
data class ExpenseEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amountCents: Long,
    val category: String,
    val notes: String,
    val occurredOn: LocalDate,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("amountCents", amountCents)
        .put("category", category)
        .put("notes", notes)
        .put("occurredOn", occurredOn.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .put("createdAtEpochMillis", createdAtEpochMillis)

    companion object {
        fun fromJson(json: JSONObject): ExpenseEntry? = runCatching {
            ExpenseEntry(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = json.optString("title"),
                amountCents = json.getLong("amountCents"),
                category = legacyCategoryNameToLabel(json.optString("category")),
                notes = json.optString("notes"),
                occurredOn = LocalDate.parse(json.getString("occurredOn")),
                createdAtEpochMillis = json.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
    }
}
