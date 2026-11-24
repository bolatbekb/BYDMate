package com.bydmate.app.fork.carhost

import org.junit.Assert.assertEquals
import org.junit.Test

class CarAppTargetsTest {
    private val nav = setOf(CarAppTargets.CATEGORY_NAV)

    private fun row(pkg: String, service: String = "S", label: String = pkg,
                    cats: Set<String> = nav, exported: Boolean = true) =
        ServiceRow(pkg, service, label, cats, exported)

    @Test fun `pick keeps exported NAVIGATION services`() {
        val out = CarAppTargets.pick(listOf(
            row("a"),
            row("b", cats = setOf("androidx.car.app.category.IOT")),
            row("c", exported = false),
        ))
        assertEquals(listOf("a"), out.map { it.pkg })
    }

    @Test fun `pick sorts by label and drops duplicates by package`() {
        val out = CarAppTargets.pick(listOf(
            row("z", "Z2", "Zeta"),
            row("z", "Z1", "Zeta"),
            row("m", "M", "Alpha"),
        ))
        assertEquals(listOf("m", "z"), out.map { it.pkg })
        assertEquals("Z1", out[1].service)
    }

    @Test fun `pick never returns our own package`() {
        val out = CarAppTargets.pick(listOf(row("com.bydmate.app"), row("x")))
        assertEquals(listOf("x"), out.map { it.pkg })
    }
}
