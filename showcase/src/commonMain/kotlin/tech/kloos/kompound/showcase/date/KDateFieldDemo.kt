package tech.kloos.kompound.showcase.date

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.date.KDateField
import tech.kloos.kompound.date.KDateRangeField
import tech.kloos.kompound.demo.DemoScope

private const val Usage_date_field = """import tech.kloos.kompound.date.KDateField

// Dates are epoch milliseconds at UTC midnight, null means no date.
var birthday by remember { mutableStateOf<Long?>(null) }

KDateField(
    value = birthday,
    onValueChange = { birthday = it },
    label = "Birthday",
    placeholder = "Pick a date",
    modifier = Modifier.fillMaxWidth(),
)"""

@KompoundDemo(
    id = "date.field",
    title = "KDateField",
    description = "Field that shows a date and opens a calendar dialog; dates are UTC-midnight epoch milliseconds.",
    category = KompoundCategory.Inputs,
    tags = ["date", "calendar", "picker", "form", "time"],
    since = "0.1.0",
    usage = Usage_date_field,
)
@Composable
fun DemoScope.KDateFieldDemo() {
    val enabled = boolControl("Enabled", true)
    val isError = boolControl("Error", false)
    var date by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.padding(16.dp)) {
        KDateField(
            value = date, onValueChange = { date = it },
            label = "Date of birth", placeholder = "Pick a date",
            supportingText = if (isError) "Date is required" else null,
            isError = isError, enabled = enabled,
        )
    }
}

private const val Usage_date_range = """import tech.kloos.kompound.date.KDateRangeField

var start by remember { mutableStateOf<Long?>(null) }
var end by remember { mutableStateOf<Long?>(null) }

KDateRangeField(
    start = start,
    end = end,
    onRangeChange = { s, e -> start = s; end = e },
    label = "Stay",
    modifier = Modifier.fillMaxWidth(),
)"""

@KompoundDemo(
    id = "date.range",
    title = "KDateRangeField",
    description = "Field for a start and end date, chosen in a calendar dialog.",
    category = KompoundCategory.Inputs,
    tags = ["date", "range", "calendar", "picker", "form", "period"],
    since = "0.1.0",
    usage = Usage_date_range,
)
@Composable
fun DemoScope.KDateRangeFieldDemo() {
    val enabled = boolControl("Enabled", true)
    var start by remember { mutableStateOf<Long?>(null) }
    var end by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KDateRangeField(start, end, { s, e -> start = s; end = e }, label = "Stay", placeholder = "Pick dates", enabled = enabled)
    }
}
