package com.bydmate.app.fork.carhost

/** Did the navigator's own screen survive the car-app session (filled in by the UI guard). */
enum class GuardVerdict { OK, CHANGED, BLOCKED, UNKNOWN }
