package com.bydmate.app.fork.cells.ui.views

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sizes of «Что делать»: compact for a short body (a 1208 px screen leaves ≈ 300 dp), roomy from 390 dp of body (a
 * 1440 px screen, ≈ 420 dp) and in a narrow window (its column scrolls).
 */
internal class WtdSize(val compact: Boolean) {
    val heroIcon = if (compact) 56.dp else 64.dp
    val heroTitle = if (compact) 26.sp else 28.sp
    val heroTitleLine = if (compact) 30.sp else 33.sp
    val heroBody = if (compact) 14.sp else 14.5.sp
    val heroBodyLine = if (compact) 18.sp else 20.sp
    val heroPad = if (compact) 6.dp else 14.dp
    val gap = if (compact) 8.dp else 10.dp
    val rowPad = if (compact) 2.dp else 3.5.dp
    val icon = if (compact) 22.dp else 28.dp
    val normPad = if (compact) 3.5.dp else 5.5.dp
    val conditionPad = if (compact) 4.dp else 5.dp
    val cardTop = if (compact) 10.dp else 12.dp
    val cardBottom = if (compact) 7.dp else 10.dp
    val footnote = !compact
}
