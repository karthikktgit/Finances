package com.ledgerline.app.finance.dashboard

import com.ledgerline.app.domain.model.ScheduledOccurrence

object HomeFeed {
    const val UPCOMING_LIMIT = 5

    fun overdue(items: List<ScheduledOccurrence>): List<ScheduledOccurrence> = items.filter { it.overdue }

    fun upcoming(items: List<ScheduledOccurrence>): List<ScheduledOccurrence> =
        items.filter { !it.overdue }.take(UPCOMING_LIMIT)
}

object ResetPolicy {
    fun resetOnLaunch(debugBuild: Boolean, enabledByDeveloper: Boolean): Boolean = debugBuild && enabledByDeveloper
}
