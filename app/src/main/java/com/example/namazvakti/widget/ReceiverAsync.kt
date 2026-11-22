package com.example.namazvakti.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import com.example.namazvakti.app.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal const val DEFAULT_RECEIVER_ASYNC_TIMEOUT_MS = 8_000L

internal fun BroadcastReceiver.launchAsync(
    context: Context,
    tag: String,
    message: String,
    timeoutMs: Long = DEFAULT_RECEIVER_ASYNC_TIMEOUT_MS,
    scope: CoroutineScope = context.appContainer().applicationScope,
    block: suspend () -> Unit
): Job {
    val pending: BroadcastReceiver.PendingResult? = goAsync()
    return launchReceiverAsync(
        pending = pending,
        tag = tag,
        message = message,
        scope = scope,
        timeoutMs = timeoutMs,
        block = block
    )
}

internal fun launchReceiverAsync(
    pending: BroadcastReceiver.PendingResult?,
    tag: String,
    message: String,
    scope: CoroutineScope,
    timeoutMs: Long = DEFAULT_RECEIVER_ASYNC_TIMEOUT_MS,
    logWarning: (String, String, Throwable) -> Unit = { warningTag, warningMessage, warning ->
        Log.w(warningTag, warningMessage, warning)
    },
    logError: (String, String, Throwable) -> Unit = { errorTag, errorMessage, error ->
        Log.e(errorTag, errorMessage, error)
    },
    finishPendingResult: () -> Unit = { pending?.finish() },
    block: suspend () -> Unit
): Job = scope.launch {
    try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        logWarning(tag, "$message (timed out after ${timeoutMs}ms)", e)
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logError(tag, message, e)
    } finally {
        finishPendingResult()
    }
}
