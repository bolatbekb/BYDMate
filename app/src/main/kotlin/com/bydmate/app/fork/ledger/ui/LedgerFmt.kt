package com.bydmate.app.fork.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bydmate.app.util.appLocalizedContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Number and date formatting for the ledger screens, in the app language. */
internal class LedgerFmt(private val locale: Locale) {
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun money(v: Double): String = String.format(locale, "%,.2f", v)
    fun two(v: Double): String = String.format(locale, "%.2f", v)
    fun one(v: Double): String = String.format(locale, "%.1f", v)
    fun km(v: Double): String = String.format(locale, "%,.0f", v)
    fun signedPct(v: Double): String = String.format(locale, "%+.1f%%", v)
    fun date(ms: Long): String = local(ms).format(date)
    fun time(ms: Long): String = local(ms).format(time)
    fun month(m: YearMonth): String =
        m.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + m.year

    private fun local(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
}

@Composable
internal fun rememberLedgerFmt(): LedgerFmt {
    val context = LocalContext.current
    return remember(context) {
        LedgerFmt(context.appLocalizedContext().resources.configuration.locales[0])
    }
}
