package com.bydmate.app.fork.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Fork-only database for refuels and expenses; separate from the author's AppDatabase. */
@Database(
    entities = [
        RefuelEntity::class,
        ExpenseCategoryEntity::class,
        ExpenseEntity::class,
        LedgerSettingEntity::class,
    ],
    version = LedgerDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun refuelDao(): RefuelDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): ExpenseCategoryDao
    abstract fun settingDao(): LedgerSettingDao

    companion object {
        const val FILE_NAME = "fork_ledger.db"
        const val DEMO_FILE_NAME = "fork_ledger_demo.db"
        const val SCHEMA_VERSION = 1

        /** Display name comes from string `ledger_cat_<key>`; the ru name is the stored fallback. */
        val BUILTIN_CATEGORIES: List<String> = listOf(
            "service", "repair", "tires", "wash", "accessories", "insurance",
            "registration", "parking", "tolls", "fines", "tuning", "other",
        )

        private val RU_NAMES = mapOf(
            "service" to "ТО",
            "repair" to "Ремонт",
            "tires" to "Шины",
            "wash" to "Мойка",
            "accessories" to "Аксессуары",
            "insurance" to "Страховка",
            "registration" to "Регистрация и налог",
            "parking" to "Парковка",
            "tolls" to "Платные дороги",
            "fines" to "Штрафы",
            "tuning" to "Тюнинг",
            "other" to "Прочее",
        )

        fun seedCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                BUILTIN_CATEGORIES.forEachIndexed { index, key ->
                    db.execSQL(
                        "INSERT INTO expense_categories (name, builtin_key, color, sort, archived) " +
                            "VALUES (?, ?, NULL, ?, 0)",
                        arrayOf<Any>(RU_NAMES.getValue(key), key, index),
                    )
                }
            }
        }

        fun build(context: Context, fileName: String): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, fileName)
                .addCallback(seedCallback())
                .build()
    }
}
