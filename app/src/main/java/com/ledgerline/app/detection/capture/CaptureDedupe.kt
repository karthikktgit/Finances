package com.ledgerline.app.detection.capture

import com.ledgerline.app.domain.model.CapturedSpend

/**
 * The same payment often arrives twice: a bank text and a wallet notice, or a notification that is updated in place.
 * A new capture is the same payment as an earlier one when the amount and direction match and it arrived within
 * [WINDOW_MILLIS]. Two genuinely separate equal purchases inside that window are rare, and the second can still be
 * added by hand.
 */
object CaptureDedupe {
    const val WINDOW_MILLIS = 20 * 60 * 1000L

    /**
     * A different app reporting the same amount can be hours late: a wallet notice at the till, then the bank's email
     * that evening. From another app, the same amount and direction within this window is the same payment.
     */
    const val OTHER_SOURCE_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

    fun windowStart(capturedAtEpochMillis: Long): Long = capturedAtEpochMillis - WINDOW_MILLIS

    fun otherSourceWindowStart(capturedAtEpochMillis: Long): Long = capturedAtEpochMillis - OTHER_SOURCE_WINDOW_MILLIS

    fun isSamePayment(earlier: CapturedSpend, later: CapturedSpend): Boolean {
        if (earlier.amountMinor != later.amountMinor || earlier.direction != later.direction) return false
        val gap = later.capturedAtEpochMillis - earlier.capturedAtEpochMillis
        if (gap < 0) return false
        return gap <= WINDOW_MILLIS || (earlier.packageName != later.packageName && gap <= OTHER_SOURCE_WINDOW_MILLIS)
    }
}
