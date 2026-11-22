package com.bydmate.app.fork.cells.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** fork-only database of the battery check; separate from the author's AppDatabase. */
@Database(entities = [CellSnapshotEntity::class], version = 1, exportSchema = true)
abstract class CellsDatabase : RoomDatabase() {
    abstract fun dao(): CellDao

    companion object {
        const val FILE_NAME = "fork_cells.db"
        const val DEMO_FILE_NAME = "fork_cells_demo.db"

        fun build(context: Context, fileName: String): CellsDatabase =
            Room.databaseBuilder(context.applicationContext, CellsDatabase::class.java, fileName).build()
    }
}
