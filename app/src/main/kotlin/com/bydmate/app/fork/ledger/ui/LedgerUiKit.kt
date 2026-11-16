package com.bydmate.app.fork.ledger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.data.ExpenseCategoryEntity
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
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

/** Builtin categories show their translated name; the stored name is the user's own. */
@Composable
internal fun categoryLabel(c: ExpenseCategoryEntity): String = when (c.builtinKey) {
    "service" -> stringResource(R.string.ledger_cat_service)
    "repair" -> stringResource(R.string.ledger_cat_repair)
    "tires" -> stringResource(R.string.ledger_cat_tires)
    "wash" -> stringResource(R.string.ledger_cat_wash)
    "accessories" -> stringResource(R.string.ledger_cat_accessories)
    "insurance" -> stringResource(R.string.ledger_cat_insurance)
    "registration" -> stringResource(R.string.ledger_cat_registration)
    "parking" -> stringResource(R.string.ledger_cat_parking)
    "tolls" -> stringResource(R.string.ledger_cat_tolls)
    "fines" -> stringResource(R.string.ledger_cat_fines)
    "tuning" -> stringResource(R.string.ledger_cat_tuning)
    "other" -> stringResource(R.string.ledger_cat_other)
    else -> c.name
}

@Composable
internal fun LedgerField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = true,
    error: Boolean = false,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it, maxLines = 1) } },
        singleLine = true,
        isError = error,
        supportingText = when {
            error -> { { Text(stringResource(R.string.ledger_error_positive)) } }
            supporting != null -> { { Text(supporting, color = TextSecondary) } }
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = AccentGreen,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AccentGreen,
            errorBorderColor = SocRed,
            errorLabelColor = SocRed,
            errorSupportingTextColor = SocRed,
        ),
        modifier = modifier,
    )
}

/** Date and time chips opening the platform pickers; [onChange] gets the new epoch millis. */
@Composable
internal fun LedgerDateTimeRow(ms: Long, fmt: LedgerFmt, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    fun push(next: LocalDateTime) = onChange(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LedgerButton("${stringResource(R.string.ledger_field_date)}: ${fmt.date(ms)}", primary = false) {
            DatePickerDialog(
                context,
                { _, y, m, d -> push(at.withYear(y).withMonth(m + 1).withDayOfMonth(d)) },
                at.year, at.monthValue - 1, at.dayOfMonth,
            ).show()
        }
        LedgerButton("${stringResource(R.string.ledger_field_time)}: ${fmt.time(ms)}", primary = false) {
            TimePickerDialog(context, { _, h, min -> push(at.withHour(h).withMinute(min)) }, at.hour, at.minute, true).show()
        }
    }
}

@Composable
internal fun LedgerButton(label: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = NavyDark),
        ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CardBorder),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurfaceElevated, contentColor = TextPrimary),
        ) { Text(label, fontSize = 13.sp, maxLines = 1) }
    }
}

@Composable
internal fun LedgerCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onChange(!checked) },
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = AccentGreen, uncheckedColor = CardBorder, checkmarkColor = NavyDark),
        )
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
    }
}
