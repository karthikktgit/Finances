package com.ledgerline.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * How screens move, so the direction always tells you where you are:
 * - Between tabs: a quick cross-fade, because tabs are siblings, not deeper or shallower.
 * - Into something you browse (details, settings, lists): it slides in from the right; back slides it out to the
 *   right while the screen underneath slides back from slightly left.
 * - Into an editor (add or edit a spend, bill, budget, account, note): it rises from the bottom like a sheet and
 *   drops back down on back. The screen underneath stays still.
 *
 * Going back uses linear timing. The back gesture scrubs this same animation with the finger, so linear timing
 * keeps the page exactly under the finger, and letting go simply finishes the same motion with no jump.
 */
object LedgerMotion {
    private const val TAB_MS = 180
    private const val PUSH_MS = 320
    private const val POP_MS = 260

    fun isSheet(destination: NavDestination): Boolean =
        destination.hasRoute<TransactionEditorRoute>() ||
            destination.hasRoute<RecurringEditorRoute>() ||
            destination.hasRoute<BudgetEditorRoute>() ||
            destination.hasRoute<AccountEditorRoute>() ||
            destination.hasRoute<ExpenseNoteEditorRoute>() ||
            destination.hasRoute<GoalEditorRoute>()

    private fun bothTabs(from: NavDestination, to: NavDestination) = from.isAnyTab() && to.isAnyTab()

    private fun <T> push() = tween<T>(PUSH_MS, easing = FastOutSlowInEasing)

    private fun <T> pop() = tween<T>(POP_MS, easing = LinearEasing)

    fun enter(from: NavDestination, to: NavDestination): EnterTransition = when {
        bothTabs(from, to) -> fadeIn(tween(TAB_MS))
        isSheet(to) -> slideInVertically(push()) { it }
        else -> slideInHorizontally(push()) { it }
    }

    fun exit(from: NavDestination, to: NavDestination): ExitTransition = when {
        bothTabs(from, to) -> fadeOut(tween(TAB_MS))
        // Stay put under a sheet until it fully covers this screen.
        isSheet(to) -> fadeOut(tween(PUSH_MS), targetAlpha = 1f)
        else -> slideOutHorizontally(push()) { -it / 4 }
    }

    fun popEnter(from: NavDestination, to: NavDestination): EnterTransition = when {
        bothTabs(from, to) -> fadeIn(tween(TAB_MS))
        isSheet(from) -> EnterTransition.None
        else -> slideInHorizontally(pop()) { -it / 4 }
    }

    fun popExit(from: NavDestination, to: NavDestination): ExitTransition = when {
        bothTabs(from, to) -> fadeOut(tween(TAB_MS))
        isSheet(from) -> slideOutVertically(pop()) { it }
        else -> slideOutHorizontally(pop()) { it }
    }
}
