package com.ledgerline.app.ui.undo

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object UndoTiming {
    const val VISIBLE_MILLIS = 1_800L
}

suspend fun SnackbarHostState.showBriefUndo(message: String, onUndo: suspend () -> Unit) {
    coroutineScope {
        val timer = launch {
            delay(UndoTiming.VISIBLE_MILLIS)
            currentSnackbarData?.dismiss()
        }
        val result = showSnackbar(
            message = message,
            actionLabel = "Undo",
            duration = SnackbarDuration.Indefinite,
        )
        timer.cancel()
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}
