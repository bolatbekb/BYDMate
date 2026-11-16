package com.bydmate.app.fork.ledger.domain

import com.bydmate.app.data.remote.DiParsData

object LedgerParking {
    fun isParked(data: DiParsData?): Boolean =
        data == null || ((data.speed ?: 0) == 0 && data.gear != 2 && data.gear != 4)
}
