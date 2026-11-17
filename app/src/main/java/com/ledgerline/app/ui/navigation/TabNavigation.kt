package com.ledgerline.app.ui.navigation

/**
 * Bottom tabs share one start destination. Popping back to Home with saveState records the
 * screen above Home under Home's id. Restoring state on that same Home navigation puts the
 * screen back, so Home never appears. Home therefore pops without restoring. Other tabs still restore.
 */
object TabNavigation {
    fun opensDestination(currentIsTarget: Boolean): Boolean = !currentIsTarget

    fun restoreSavedState(targetIsHome: Boolean): Boolean = !targetIsHome
}
