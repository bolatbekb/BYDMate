package com.bydmate.app.fork.cells.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.ui.home.graphite.GrFont
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.home.graphite.GraphiteLookState
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.Skin
import com.bydmate.app.ui.theme.SkinState
import com.bydmate.app.ui.theme.Skins
import com.bydmate.app.ui.theme.SocRed
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The normal-cell tile of the pack grid (RulerView «Линейка») is the skin's soft good-green, laid
 * over the card. These assertions pin the fix: the old grey vanished on Graphite (border ≈ card);
 * the green must read on every skin's card, stay green and distinct from the orange/red deviations,
 * and carry a readable number. fork-local.
 */
class NormalTileColorTest {
    private val ctx = mockk<Context>(relaxed = true)

    @After fun reset() {
        SkinState.select(ctx, Skins.CLASSIC)
        GraphiteLookState.selectScheme(ctx, GrScheme.DARK)
        GraphiteLookState.selectFont(ctx, GrFont.INTER)
    }

    /** Sum of per-channel differences, 0 (same) … 3 (opposite). */
    private fun dist(a: Color, b: Color): Float =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)

    /** Runs [body] once per visible appearance of every skin (each Graphite scheme counts). */
    private fun eachSkin(body: (label: String) -> Unit) {
        Skins.ALL.forEach { skin ->
            SkinState.select(ctx, skin)
            if (skin.isGraphite) {
                GrScheme.entries.forEach { s ->
                    GraphiteLookState.selectScheme(ctx, s)
                    body("${skin.id}/${s.name}")
                }
            } else {
                body(skin.id)
            }
        }
    }

    @Test fun `the tile is a soft green, not a full-opacity accent block`() = eachSkin { label ->
        val tile = normalTileColor()
        assertTrue("$label alpha ${tile.alpha}", tile.alpha in 0.25f..0.75f)
        // it keeps the skin's good-green hue, not the old grey
        val green = ConsumptionGood
        assertTrue("$label not green: $green", green.green > green.red && green.green > green.blue)
        assertNotEquals("$label still the old grey", classColor(CellClass.NORMAL), tile)
    }

    @Test fun `the tile is clearly visible on every skin's card`() = eachSkin { label ->
        val onCard = normalTileColor().compositeOver(CardSurface)
        assertTrue("$label invisible on card (d=${dist(onCard, CardSurface)})", dist(onCard, CardSurface) > 0.15f)
        // and it still reads as green once mixed with the dark card
        assertTrue("$label not green on card: $onCard", onCard.green > onCard.red && onCard.green > onCard.blue)
    }

    @Test fun `the tile stays distinct from the orange and red deviation tiles`() = eachSkin { label ->
        val onCard = normalTileColor().compositeOver(CardSurface)
        assertTrue("$label too close to orange", dist(onCard, AccentOrange) > 0.3f)
        assertTrue("$label too close to red", dist(onCard, SocRed) > 0.3f)
    }

    @Test fun `the tile number is light enough to read on the green`() = eachSkin { label ->
        val onCard = normalTileColor().compositeOver(CardSurface)
        val ink = normalTileInk().compositeOver(onCard)
        assertTrue("$label number too dim (tile ${onCard.luminance()}, ink ${ink.luminance()})",
            ink.luminance() > onCard.luminance() + 0.06f)
    }

    @Test fun `on Graphite the old grey vanished while the green reads`() {
        SkinState.select(ctx, Skins.GRAPHITE_HOME)
        GraphiteLookState.selectScheme(ctx, GrScheme.DARK)
        // the old grid tile: the card border at 0.55 — on Graphite the border is almost the card, so
        // it barely shifted it (a faint, near-neutral grey); the green is a far clearer, coloured tile
        val oldGrey = CardBorder.copy(alpha = 0.55f).compositeOver(CardSurface)
        val newGreen = normalTileColor().compositeOver(CardSurface)
        val oldD = dist(oldGrey, CardSurface)
        val newD = dist(newGreen, CardSurface)
        assertTrue("old grey already strong (d=$oldD)", oldD < 0.15f)
        assertTrue("new green hidden (d=$newD)", newD > 0.15f)
        assertTrue("green no clearer than the old grey (old=$oldD new=$newD)", newD > oldD * 2.5f)
    }

    @Test fun `every skin's good-green is a real green, nothing accidental`() {
        // all skins share the traffic-light socGreen; pin it so a future skin override is a choice
        assertEquals(Color(0xFF4ADE80), Skins.CLASSIC.palette.socGreen)
        Skins.ALL.forEach { skin: Skin ->
            SkinState.select(ctx, skin)
            if (!skin.isGraphite) {
                val g = ConsumptionGood
                assertTrue("${skin.id} good colour is not green: $g", g.green > g.red && g.green > g.blue)
            }
        }
    }
}
