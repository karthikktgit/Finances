package com.ledgerline.app.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Financial dates use the device zone so a transaction entered "today" does not
 * shift to the previous or next calendar day because of UTC conversion.
 */
interface AppClock {
    fun zone(): ZoneId

    fun today(): LocalDate

    fun now(): Instant
}

class SystemAppClock @Inject constructor() : AppClock {
    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun today(): LocalDate = LocalDate.now(zone())

    override fun now(): Instant = Instant.now()
}
