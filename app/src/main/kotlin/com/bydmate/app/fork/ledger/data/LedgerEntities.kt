package com.bydmate.app.fork.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "refuels",
    indices = [
        Index(value = ["import_key"], unique = true),
        Index(value = ["date"]),
    ],
)
data class RefuelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double?,
    val liters: Double,
    @ColumnInfo(name = "total_cost") val totalCost: Double?,
    @ColumnInfo(name = "price_per_liter") val pricePerLiter: Double?,
    @ColumnInfo(name = "full_tank") val fullTank: Boolean,
    @ColumnInfo(name = "missed_before") val missedBefore: Boolean = false,
    @ColumnInfo(name = "car_fuel_total_l") val carFuelTotalL: Double?,
    @ColumnInfo(name = "tank_percent_before") val tankPercentBefore: Int?,
    @ColumnInfo(name = "tank_percent_after") val tankPercentAfter: Int?,
    val lat: Double?,
    val lon: Double?,
    val place: String?,
    val note: String?,
    /** "manual" | "fuelio" | "detected" */
    val source: String,
    /** "confirmed" | "draft" */
    val status: String,
    @ColumnInfo(name = "import_key") val importKey: String?,
)

@Entity(
    tableName = "expense_categories",
    indices = [Index(value = ["builtin_key"], unique = true)],
)
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "builtin_key") val builtinKey: String?,
    val color: String?,
    val sort: Int,
    val archived: Boolean = false,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["import_key"], unique = true),
        Index(value = ["category_id"]),
        Index(value = ["date"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double?,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val amount: Double,
    val title: String?,
    val note: String?,
    val source: String,
    @ColumnInfo(name = "import_key") val importKey: String?,
)

@Entity(tableName = "ledger_settings")
data class LedgerSettingEntity(
    @PrimaryKey val key: String,
    val value: String?,
)
