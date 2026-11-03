package com.cris.sumptus.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONObject

/**
 * A transfer of money between the everyday "Saldo" and the general investment pot
 * (e.g. a Trade Republic remunerated cash account). Positive amounts move money out of
 * the Saldo into the pot; negative amounts move money back from the pot into the Saldo.
 *
 * These transfers reduce or increase the available Saldo, but are intentionally kept out
 * of [ExpenseEntry]/[ExtraEntry] so they never affect the monthly income/expense totals.
 */
data class InvestmentTransfer(
    val id: String = UUID.randomUUID().toString(),
    val amountCents: Long,
    val notes: String = "",
    val occurredOn: LocalDate,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("amountCents", amountCents)
        .put("notes", notes)
        .put("occurredOn", occurredOn.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .put("createdAtEpochMillis", createdAtEpochMillis)

    companion object {
        fun fromJson(json: JSONObject): InvestmentTransfer? = runCatching {
            InvestmentTransfer(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                amountCents = json.getLong("amountCents"),
                notes = json.optString("notes"),
                occurredOn = LocalDate.parse(json.getString("occurredOn")),
                createdAtEpochMillis = json.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
    }
}

/**
 * A specific investment (a stock, a fund...) funded from the general pot.
 *
 * Its current value is `invested + gainCents`, where `invested` is the sum of its
 * [InvestmentAllocation]s. Storing the gain instead of an absolute value keeps the value
 * correct when more money is later moved in or out of the investment.
 */
data class Investment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gainCents: Long = 0L,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("gainCents", gainCents)
        .put("createdAtEpochMillis", createdAtEpochMillis)

    companion object {
        fun fromJson(json: JSONObject): Investment? = runCatching {
            Investment(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = json.getString("name"),
                gainCents = json.optLong("gainCents", 0L),
                createdAtEpochMillis = json.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
    }
}

/**
 * A movement of money between the general pot and one specific [Investment]. Positive amounts
 * move money out of the pot into the investment; negative amounts move it back into the pot.
 */
data class InvestmentAllocation(
    val id: String = UUID.randomUUID().toString(),
    val investmentId: String,
    val amountCents: Long,
    val notes: String = "",
    val occurredOn: LocalDate,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("investmentId", investmentId)
        .put("amountCents", amountCents)
        .put("notes", notes)
        .put("occurredOn", occurredOn.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .put("createdAtEpochMillis", createdAtEpochMillis)

    companion object {
        fun fromJson(json: JSONObject): InvestmentAllocation? = runCatching {
            InvestmentAllocation(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                investmentId = json.getString("investmentId"),
                amountCents = json.getLong("amountCents"),
                notes = json.optString("notes"),
                occurredOn = LocalDate.parse(json.getString("occurredOn")),
                createdAtEpochMillis = json.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
    }
}

/** [InvestmentValuation.targetId] used for the general pot instead of an investment id. */
const val GENERAL_POT_ID = "general"

/**
 * The value of the general pot or of one investment as of a date, stored as the difference from
 * the money put in plus the [InvestmentInterest]s up to that date. From that date on, the value
 * is `invested + interest + gainCents` until a later valuation replaces it, so updating September never touches October or November values
 * that were set on their own. There is at most one valuation per target and month.
 */
data class InvestmentValuation(
    val id: String = UUID.randomUUID().toString(),
    val targetId: String,
    val occurredOn: LocalDate,
    val gainCents: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("targetId", targetId)
        .put("occurredOn", occurredOn.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .put("gainCents", gainCents)
        .put("createdAtEpochMillis", createdAtEpochMillis)

    companion object {
        fun fromJson(json: JSONObject): InvestmentValuation? = runCatching {
            InvestmentValuation(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                targetId = json.getString("targetId"),
                occurredOn = LocalDate.parse(json.getString("occurredOn")),
                gainCents = json.getLong("gainCents"),
                createdAtEpochMillis = json.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
    }
}

/** Turns the old undated gains (one per investment plus the pot's) into valuations dated [date]. */
internal fun legacyGainsToValuations(
    investments: List<Investment>,
    potGainCents: Long,
    date: LocalDate,
): List<InvestmentValuation> = buildList {
    if (potGainCents != 0L) add(InvestmentValuation(targetId = GENERAL_POT_ID, occurredOn = date, gainCents = potGainCents))
    investments.filter { it.gainCents != 0L }.forEach {
        add(InvestmentValuation(targetId = it.id, occurredOn = date, gainCents = it.gainCents))
    }
}

/**
 * Interest or return earned by the general pot or one investment on a date (e.g. the monthly
 * interest of the remunerated account). It adds to the value from that date on. Negative
 * amounts are losses.
 */
data class InvestmentInterest(
    val id: String = UUID.randomUUID().toString(),
    val targetId: String,
    val amountCents: Long,
    val occurredOn: LocalDate,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("targetId", targetId)
        .put("amountCents", amountCents)
        .put("occurredOn", occurredOn.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .put("createdAtEpochMillis", createdAtEpochMillis)

    companion object {
        fun fromJson(json: JSONObject): InvestmentInterest? = runCatching {
            InvestmentInterest(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                targetId = json.getString("targetId"),
                amountCents = json.getLong("amountCents"),
                occurredOn = LocalDate.parse(json.getString("occurredOn")),
                createdAtEpochMillis = json.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
    }
}
