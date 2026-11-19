package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalStrings
import com.mdmoney.domain.Month
import com.mdmoney.importer.SimpleDate
import com.mdmoney.ui.Period
import com.mdmoney.ui.RangeDraft
import com.mdmoney.ui.YearMonth
import com.mdmoney.ui.monthGrid
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.toggled

/**
 * Picks what Home covers: up to twelve months from a year grid, or a first and last day on a
 * calendar. [current] is the period already showing (null for a single month), [year]/[month] where
 * a fresh pick starts.
 */
@Composable
fun PeriodDialog(
    current: Period?,
    year: Int,
    month: Month,
    onDismiss: () -> Unit,
    onApply: (Period) -> Unit,
    onBackToMonth: () -> Unit,
) {
    val s = LocalStrings.current
    var tab by remember { mutableStateOf(if (current is Period.Range) 1 else 0) }
    var months by remember {
        mutableStateOf((current as? Period.Months)?.months ?: setOf(YearMonth(year, month.number)))
    }
    var draft by remember {
        mutableStateOf((current as? Period.Range)?.let { RangeDraft(it.start, it.end) } ?: RangeDraft())
    }
    val period: Period? = if (tab == 0) months.takeIf { it.isNotEmpty() }?.let { Period.Months(it) } else draft.toPeriod()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment(s.periodMonths, tab == 0, Modifier.weight(1f)) { tab = 0 }
                Segment(s.periodRange, tab == 1, Modifier.weight(1f)) { tab = 1 }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    MonthsPicker(months, startYear = (current as? Period.Months)?.months?.min()?.year ?: year) { months = months.toggled(it) }
                } else {
                    RangePicker(draft, startYear = draft.start?.year ?: year, startMonth = draft.start?.month ?: month.number) {
                        draft = draft.tap(it)
                    }
                }
                if (current != null) {
                    TextButton(onClick = onBackToMonth) { Text(s.periodBackToMonth) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = period != null, onClick = { period?.let(onApply) }) { Text(s.periodApply) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) D.dark else D.field).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = if (selected) D.onDark else D.ink,
        )
    }
}

/** A year at a time, twelve month chips; picks persist when the year is changed. */
@Composable
private fun MonthsPicker(selected: Set<YearMonth>, startYear: Int, onToggle: (YearMonth) -> Unit) {
    val s = LocalStrings.current
    var viewYear by remember { mutableStateOf(startYear) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper("$viewYear", onPrev = { viewYear-- }, onNext = { viewYear++ })
        Month.ALL.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val ym = YearMonth(viewYear, m.number)
                    val on = ym in selected
                    // A thirteenth month is refused by the model; show it as unavailable too.
                    val full = !on && selected.size >= Period.MAX_MONTHS
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) D.dark else D.field)
                            .clickable(enabled = !full) { onToggle(ym) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.monthShort(m),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Medium,
                            color = if (on) D.onDark else if (full) D.soft else D.ink,
                        )
                    }
                }
            }
        }
        Text(s.monthsOfTwelve(selected.size), style = MaterialTheme.typography.bodySmall, color = D.soft)
    }
}

/** A calendar month at a time; the range being drawn is highlighted across month changes. */
@Composable
private fun RangePicker(draft: RangeDraft, startYear: Int, startMonth: Int, onTap: (SimpleDate) -> Unit) {
    val s = LocalStrings.current
    var view by remember { mutableStateOf(YearMonth(startYear, startMonth)) }
    fun shift(delta: Int) {
        val index = view.year * 12 + (view.month - 1) + delta
        view = YearMonth(index / 12, index % 12 + 1)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Stepper(
            "${s.month(Month.fromNumber(view.month)!!)} ${view.year}",
            onPrev = { shift(-1) },
            onNext = { shift(1) },
        )
        Row(Modifier.fillMaxWidth()) {
            s.weekdayInitials.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = D.soft,
                )
            }
        }
        monthGrid(view.year, view.month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Box(Modifier.weight(1f).height(38.dp))
                    } else {
                        val edge = day == draft.start || day == draft.end
                        Box(
                            Modifier.weight(1f).height(38.dp).padding(1.dp).clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        edge -> D.dark
                                        draft.covers(day) -> D.field
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                )
                                .clickable { onTap(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${day.day}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = if (edge) D.onDark else D.ink,
                            )
                        }
                    }
                }
            }
        }
        Text(
            if (draft.start == null || draft.end != null) s.pickFirstDay else s.pickLastDay,
            style = MaterialTheme.typography.bodySmall,
            color = D.soft,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Stepper(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(40.dp).clickable(onClick = onPrev), contentAlignment = Alignment.Center) {
            Text("‹", style = MaterialTheme.typography.titleLarge, color = D.ink)
        }
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = D.ink)
        Box(Modifier.size(40.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Text("›", style = MaterialTheme.typography.titleLarge, color = D.ink)
        }
    }
}
