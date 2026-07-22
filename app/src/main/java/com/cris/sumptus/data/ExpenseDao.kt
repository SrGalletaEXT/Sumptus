package com.cris.sumptus.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query(
        """
        SELECT * FROM expenses
        ORDER BY occurredOn DESC, createdAtEpochMillis DESC
        """,
    )
    fun observeExpenses(): Flow<List<ExpenseEntry>>

    @Query(
        """
        SELECT * FROM expenses
        ORDER BY occurredOn DESC, createdAtEpochMillis DESC
        """,
    )
    suspend fun getExpenses(): List<ExpenseEntry>

    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntry)

    @Upsert
    suspend fun upsertExpenses(expenses: List<ExpenseEntry>)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Transaction
    suspend fun replaceExpenses(expenses: List<ExpenseEntry>) {
        deleteAllExpenses()
        upsertExpenses(expenses)
    }

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun countExpenses(): Int
}
