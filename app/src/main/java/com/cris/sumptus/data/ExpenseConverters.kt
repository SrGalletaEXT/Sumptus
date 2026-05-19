package com.cris.sumptus.data

import androidx.room.TypeConverter
import java.time.LocalDate

class ExpenseConverters {
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun expenseCategoryToName(value: ExpenseCategory?): String? = value?.name

    @TypeConverter
    fun nameToExpenseCategory(value: String?): ExpenseCategory? = value?.let(ExpenseCategory::fromName)
}
