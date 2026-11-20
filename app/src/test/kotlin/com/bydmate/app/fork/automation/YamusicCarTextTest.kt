package com.bydmate.app.fork.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.automation.RuleParseResult
import com.bydmate.app.data.automation.RuleShare
import com.bydmate.app.data.automation.SharedRule
import com.bydmate.app.data.local.LocalePreferences
import com.bydmate.app.data.local.entity.ActionDef
import com.bydmate.app.data.local.entity.TriggerDef
import com.bydmate.app.ui.automation.RuleImportSummary
import com.bydmate.app.ui.automation.actionText
import com.bydmate.app.ui.automation.newYamusicCarAction
import com.bydmate.app.util.appLocalizedContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class YamusicCarTextTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val lc: Context get() = ctx.appLocalizedContext()

    @Before fun setUp() { LocalePreferences(ctx).setLanguage("ru") }

    private fun action(payload: String) = ActionDef("", "Я.Музыка Car", "yamusic_car", payload)

    @Test fun `rule text and editor line read Я Музыка Car colon command`() {
        assertEquals("Я.Музыка Car: Пауза", actionText(action("""{"command":"pause"}"""), lc))
        assertEquals("Я.Музыка Car: Пауза", yamusicCarLine("""{"command":"pause"}""", lc))
    }

    @Test fun `likes has no doubled guillemets`() {
        assertEquals("Я.Музыка Car: «Мне нравится»", actionText(action("""{"command":"likes"}"""), lc))
        assertEquals("Я.Музыка Car: «Мне нравится» вперемешку", yamusicCarLine("""{"command":"likes_shuffle"}""", lc))
    }

    @Test fun `a new action carries the command in its display name`() {
        assertEquals("Я.Музыка Car: Моя волна", newYamusicCarAction(lc).displayName)
    }

    @Test fun `import preview shows the id and the show flag`() {
        assertEquals("Я.Музыка Car: Волна по плейлисту, ID: 12345_3, показать плеер",
            RuleImportSummary.action(action("""{"command":"wave_playlist","uid":"12345","kind":"3","show":true}"""), ctx))
        assertEquals("Я.Музыка Car: Волна по исполнителю, ID: ab_1",
            RuleImportSummary.action(action("""{"command":"wave_artist","id":"ab_1","show":false}"""), ctx))
        assertEquals("Я.Музыка Car: Пауза", RuleImportSummary.action(action("""{"command":"pause"}"""), ctx))
    }

    @Test fun `share round trip keeps the payload and parses as ok`() {
        val payload = """{"command":"wave_playlist","uid":"12345","kind":"3","show":true}"""
        val rule = SharedRule(
            name = "Музыка", triggerLogic = "AND",
            triggers = listOf(TriggerDef("Speed", "车速", ">", "7", "Скорость")),
            actions = listOf(action(payload)), cooldownSeconds = 60, requirePark = false,
            confirmBeforeExecute = false, fireOncePerTrip = false, playSound = false,
        )
        val result = RuleShare.parse(RuleShare.exportJson(rule, "x"), "Звонок")
        assertTrue(result.toString(), result is RuleParseResult.Ok)
        val back = (result as RuleParseResult.Ok).rule.actions.single()
        assertEquals("yamusic_car", back.kind)
        assertEquals(payload, back.payload)
    }
}
