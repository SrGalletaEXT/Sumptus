package com.cris.sumptus.data

import androidx.room.Dao
import androidx.room.Query
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

    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntry)

    @Upsert
    suspend fun upsertExpenses(expenses: List<ExpenseEntry>)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String)

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun countExpenses(): Int
}
