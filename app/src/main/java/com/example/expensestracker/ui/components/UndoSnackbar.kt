package com.example.expensestracker.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows [message] with an "Undo" action on [hostState]; calls [onUndo] only if the action is
 * tapped before the snackbar times out or gets pushed out by another one. The caller has already
 * committed the action it's offering to undo (e.g. the delete already happened) - this is purely
 * the "tap to reverse it" feedback, not a deferred commit.
 */
fun CoroutineScope.showUndoSnackbar(hostState: SnackbarHostState, message: String, undoLabel: String, onUndo: () -> Unit) {
    launch {
        val result = hostState.showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}
