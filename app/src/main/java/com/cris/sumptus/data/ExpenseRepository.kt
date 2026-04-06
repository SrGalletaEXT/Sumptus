package com.cris.sumptus.data

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val expensesKey = stringPreferencesKey("expenses_json")

class ExpenseRepository(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(context.filesDir, "sumptus.preferences_pb") },
    )

    val expenses: Flow<List<ExpenseEntry>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            decode(preferences[expensesKey].orEmpty())
        }

    suspend fun addExpense(expense: ExpenseEntry) {
        dataStore.edit { preferences ->
            val current = decode(preferences[expensesKey].orEmpty()).toMutableList()
            current.add(expense)
            preferences[expensesKey] = encode(current)
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        dataStore.edit { preferences ->
            val updated = decode(preferences[expensesKey].orEmpty())
                .filterNot { it.id == expenseId }
            preferences[expensesKey] = encode(updated)
        }
    }

    private fun decode(rawJson: String): List<ExpenseEntry> {
        if (rawJson.isBlank()) {
            return emptyList()
        }

        return runCatching {
            val items = JSONArray(rawJson)
            buildList {
                for (index in 0 until items.length()) {
                    ExpenseEntry.fromJson(items.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList()).sortedWith(
            compareByDescending<ExpenseEntry> { it.occurredOn }
                .thenByDescending { it.createdAtEpochMillis },
        )
    }

    private fun encode(expenses: List<ExpenseEntry>): String {
        val items = JSONArray()
        expenses
            .sortedWith(
                compareByDescending<ExpenseEntry> { it.occurredOn }
                    .thenByDescending { it.createdAtEpochMillis },
            )
            .forEach { expense ->
                items.put(expense.toJson())
            }
        return items.toString()
    }
}
