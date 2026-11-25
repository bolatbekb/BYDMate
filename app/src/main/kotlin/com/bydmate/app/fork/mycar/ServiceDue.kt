package com.bydmate.app.fork.mycar

/** The regular service is «soon» within 1000 km or 30 days of the countdown. Null = unknown, not soon. */
fun serviceSoon(kmLeft: Int?, daysLeft: Int?): Boolean =
    (kmLeft != null && kmLeft <= KM_SOON) || (daysLeft != null && daysLeft <= DAYS_SOON)

private const val KM_SOON = 1000
private const val DAYS_SOON = 30
