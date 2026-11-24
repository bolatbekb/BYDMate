package com.bydmate.app.fork.carhost

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.car.app.connection.CarConnection

/**
 * Answers the car-app library's `CarConnection` query (authority `androidx.car.app.connection`,
 * one row, column `CarConnectionState`): projection while a probe session runs, else not
 * connected. Read-only; probe build only.
 */
class CarConnectionProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(arrayOf(COLUMN), 1).apply {
        addRow(arrayOf<Any>(if (CarHostProbe.anyConnected) TYPE_PROJECTION else TYPE_NOT_CONNECTED))
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        // The library's own names (1.4.0: "CarConnectionState", 0 / 2, the update action below).
        const val COLUMN = CarConnection.CAR_CONNECTION_STATE
        const val TYPE_NOT_CONNECTED = CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        const val TYPE_PROJECTION = CarConnection.CONNECTION_TYPE_PROJECTION
        const val ACTION_UPDATED = CarConnection.ACTION_CAR_CONNECTION_UPDATED
    }
}
