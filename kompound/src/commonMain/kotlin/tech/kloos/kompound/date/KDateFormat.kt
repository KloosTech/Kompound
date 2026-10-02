package tech.kloos.kompound.date

/** Date formatting used by [KDateField] and [KDateRangeField] unless the caller passes their own. */
public object KDateFormat {
    /**
     * Formats [epochMillis] (UTC midnight, as the date pickers produce) as `yyyy-MM-dd`. Pure Kotlin with
     * no time zone or locale APIs, so it is identical on every platform. For localised output pass your
     * own `formatDate` to the date fields.
     */
    public fun iso(epochMillis: Long): String {
        // Civil-from-days algorithm (H. Hinnant), proleptic Gregorian calendar.
        val z = epochMillis.floorDiv(MILLIS_PER_DAY) + 719_468L
        val era = (if (z >= 0) z else z - 146_096L) / 146_097L
        val dayOfEra = z - era * 146_097L
        val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
        val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
        val mp = (5L * dayOfYear + 2L) / 153L
        val day = dayOfYear - (153L * mp + 2L) / 5L + 1L
        val month = if (mp < 10) mp + 3L else mp - 9L
        val year = yearOfEra + era * 400L + if (month <= 2) 1L else 0L
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}
