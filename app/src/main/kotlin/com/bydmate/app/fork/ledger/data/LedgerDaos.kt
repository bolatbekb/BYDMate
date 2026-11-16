package com.bydmate.app.fork.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class CategorySum(
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val total: Double,
)

@Dao
interface RefuelDao {
    @Query("SELECT * FROM refuels ORDER BY date DESC")
    fun observeAll(): Flow<List<RefuelEntity>>

    @Query("SELECT * FROM refuels ORDER BY date ASC")
    suspend fun getAllAsc(): List<RefuelEntity>

    /** Returns -1 when `import_key` already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: RefuelEntity): Long

    @Update
    suspend fun update(e: RefuelEntity)

    @Delete
    suspend fun delete(e: RefuelEntity)

    /** Targeted draft update (never touches other columns); returns rows changed, 0 = not a draft. */
    @Query("UPDATE refuels SET tank_percent_after = :after WHERE id = :id AND status = 'draft'")
    suspend fun updateDraftPercent(id: Long, after: Int): Int

    /** Liters change only while still 0 or equal to [prevEst] (our own estimate), atomically. */
    @Query(
        "UPDATE refuels SET tank_percent_after = :after, liters = :liters " +
            "WHERE id = :id AND status = 'draft' AND (liters = 0 OR liters = :prevEst)"
    )
    suspend fun updateDraftPercentAndLiters(id: Long, after: Int, liters: Double, prevEst: Double): Int

    @Query("SELECT * FROM refuels WHERE id = :id")
    suspend fun getById(id: Long): RefuelEntity?

    @Query("SELECT * FROM refuels WHERE status = 'draft' ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun openDraft(): RefuelEntity?

    /** Confirmed receipts only (drafts are not money spent yet); half-open range [from, to). */
    @Query("SELECT COALESCE(SUM(total_cost), 0.0) FROM refuels WHERE status = 'confirmed' AND date >= :from AND date < :to")
    suspend fun sumCost(from: Long, to: Long): Double
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: ExpenseEntity): Long

    @Update
    suspend fun update(e: ExpenseEntity)

    @Delete
    suspend fun delete(e: ExpenseEntity)

    /** Half-open range [from, to). */
    @Query(
        "SELECT category_id, SUM(amount) AS total FROM expenses " +
            "WHERE date >= :from AND date < :to GROUP BY category_id"
    )
    suspend fun sumByCategory(from: Long, to: Long): List<CategorySum>
}

@Dao
interface ExpenseCategoryDao {
    @Query("SELECT * FROM expense_categories WHERE archived = 0 ORDER BY sort ASC, id ASC")
    fun observeActive(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories ORDER BY sort ASC, id ASC")
    suspend fun getAll(): List<ExpenseCategoryEntity>

    @Insert
    suspend fun insert(e: ExpenseCategoryEntity): Long

    @Update
    suspend fun update(e: ExpenseCategoryEntity)

    @Query("SELECT * FROM expense_categories WHERE builtin_key = :key")
    suspend fun getByBuiltinKey(key: String): ExpenseCategoryEntity?
}

@Dao
interface LedgerSettingDao {
    @Query("SELECT value FROM ledger_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun set(e: LedgerSettingEntity)
}
