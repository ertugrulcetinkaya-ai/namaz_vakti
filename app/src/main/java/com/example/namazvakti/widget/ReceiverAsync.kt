package com.example.namazvakti.widget

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun BroadcastReceiver.launchAsync(
    tag: String,
    message: String,
    block: suspend () -> Unit
): Job {
    val pending: BroadcastReceiver.PendingResult? = goAsync()
    return launchReceiverAsync(pending, tag, message, block = block)
}

internal fun launchReceiverAsync(
    pending: BroadcastReceiver.PendingResult?,
    tag: String,
    message: String,
    logError: (String, String, Throwable) -> Unit = { errorTag, errorMessage, error ->
        Log.e(errorTag, errorMessage, error)
    },
    block: suspend () -> Unit
): Job = CoroutineScope(Dispatchers.IO).launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logError(tag, message, e)
    } finally {
        pending?.finish()
    }
}
