package com.cris.sumptus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ExpenseEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ExpenseConverters::class)
abstract class SumptusDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var instance: SumptusDatabase? = null

        fun getInstance(context: Context): SumptusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SumptusDatabase::class.java,
                "sumptus.db",
            ).build().also { database ->
                instance = database
            }
        }
    }
}
