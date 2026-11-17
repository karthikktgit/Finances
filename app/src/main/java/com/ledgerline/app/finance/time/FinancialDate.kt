package com.ledgerline.app.finance.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Financial dates are calendar dates. The material date picker stores UTC midnight,
 * so both directions stay on UTC and do not follow the device zone or a daylight-saving gap.
 */
object FinancialDate {
    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
