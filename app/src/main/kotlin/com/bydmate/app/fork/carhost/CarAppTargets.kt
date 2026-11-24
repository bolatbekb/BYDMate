package com.bydmate.app.fork.carhost

import android.content.Intent
import android.content.pm.PackageManager

data class CarAppTarget(val pkg: String, val service: String, val label: String)

data class ServiceRow(
    val pkg: String,
    val service: String,
    val label: String,
    val categories: Set<String>,
    val exported: Boolean,
)

/** Finds navigator apps on this device that declare a car-app service (probe build). */
object CarAppTargets {
    const val ACTION = "androidx.car.app.CarAppService"
    const val CATEGORY_NAV = "androidx.car.app.category.NAVIGATION"
    private const val OWN_PACKAGE = "com.bydmate.app"

    fun pick(rows: List<ServiceRow>): List<CarAppTarget> =
        rows.filter { it.exported && CATEGORY_NAV in it.categories && it.pkg != OWN_PACKAGE }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.pkg }, { it.service }))
            .distinctBy { it.pkg }
            .map { CarAppTarget(it.pkg, it.service, it.label) }

    fun discover(pm: PackageManager): List<CarAppTarget> {
        val intent = Intent(ACTION).addCategory(CATEGORY_NAV)
        val rows = pm.queryIntentServices(intent, PackageManager.GET_META_DATA).mapNotNull { ri ->
            val si = ri.serviceInfo ?: return@mapNotNull null
            ServiceRow(si.packageName, si.name, ri.loadLabel(pm).toString(),
                setOf(CATEGORY_NAV), si.exported)
        }
        return pick(rows)
    }
}
