package com.cris.sumptus.data

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

private val expensesKey = stringPreferencesKey("expenses_json")
private val migrationCompleteKey = booleanPreferencesKey("room_migration_complete")

class ExpenseRepository(context: Context) {
    private val expenseDao = SumptusDatabase.getInstance(context).expenseDao()
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(context.filesDir, "sumptus.preferences_pb") },
    )
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        repositoryScope.launch {
            migrateLegacyExpensesIfNeeded()
        }
    }

    val expenses: Flow<List<ExpenseEntry>> = expenseDao.observeExpenses()

    suspend fun upsertExpense(expense: ExpenseEntry) {
        expenseDao.upsertExpense(expense)
    }

    suspend fun deleteExpense(expenseId: String) {
        expenseDao.deleteExpenseById(expenseId)
    }

    private suspend fun migrateLegacyExpensesIfNeeded() {
        val preferences = dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .first()

        if (preferences[migrationCompleteKey] == true) {
            return
        }

        val legacyExpenses = decode(preferences[expensesKey].orEmpty())
        val existingExpenses = expenseDao.countExpenses()

        if (existingExpenses == 0 && legacyExpenses.isNotEmpty()) {
            expenseDao.upsertExpenses(legacyExpenses)
        }

        dataStore.edit { mutablePreferences ->
            mutablePreferences[migrationCompleteKey] = true
            mutablePreferences.remove(expensesKey)
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
}
