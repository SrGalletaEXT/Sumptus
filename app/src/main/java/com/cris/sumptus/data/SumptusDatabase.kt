package com.cris.sumptus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExpenseEntry::class],
    version = 2,
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
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { database ->
                instance = database
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses_new` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `occurredOn` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    INSERT INTO `expenses_new` (
                        `id`,
                        `title`,
                        `amountCents`,
                        `category`,
                        `notes`,
                        `occurredOn`,
                        `createdAtEpochMillis`
                    )
                    SELECT
                        `id`,
                        CASE
                            WHEN TRIM(`title`) = '' THEN
                                CASE `category`
                                    WHEN 'HOGAR' THEN 'Hogar'
                                    WHEN 'COMIDA' THEN 'Comida'
                                    WHEN 'TRANSPORTE' THEN 'Transporte'
                                    WHEN 'OCIO' THEN 'Ocio'
                                    WHEN 'SALUD' THEN 'Salud'
                                    WHEN 'SUSCRIPCIONES' THEN 'Suscripciones'
                                    ELSE 'Otros'
                                END
                            ELSE `title`
                        END,
                        `amountCents`,
                        CASE `category`
                            WHEN 'HOGAR' THEN 'Hogar'
                            WHEN 'COMIDA' THEN 'Comida'
                            WHEN 'TRANSPORTE' THEN 'Transporte'
                            WHEN 'OCIO' THEN 'Ocio'
                            WHEN 'SALUD' THEN 'Salud'
                            WHEN 'SUSCRIPCIONES' THEN 'Suscripciones'
                            ELSE 'Otros'
                        END,
                        `notes`,
                        `occurredOn`,
                        `createdAtEpochMillis`
                    FROM `expenses`
                    """.trimIndent(),
                )

                db.execSQL("DROP TABLE `expenses`")
                db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
            }
        }
    }
}
