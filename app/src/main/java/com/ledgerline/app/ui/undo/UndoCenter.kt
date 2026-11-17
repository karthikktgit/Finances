package com.ledgerline.app.ui.undo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds one reversible ledger change. A second change replaces it so Undo cannot
 * run twice or reach an older, unrelated operation.
 */
@Singleton
class UndoCenter @Inject constructor() {
    private val offers = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = offers.asSharedFlow()
    private var pending: (suspend () -> Unit)? = null

    fun offer(message: String, undo: suspend () -> Unit) {
        pending = undo
        offers.tryEmit(message)
    }

    suspend fun consume(): Boolean {
        val action = pending ?: return false
        pending = null
        action()
        return true
    }
}
