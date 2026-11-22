package com.example.namazvakti.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverAsyncTest {
    @Test
    fun nullPendingResultStillRunsTheBlock() = runBlocking {
        val completed = CompletableDeferred<Unit>()
        val loggedError = CompletableDeferred<Unit>()

        launchReceiverAsync(
            pending = null,
            tag = "test",
            message = "unexpected failure",
            scope = this,
            logError = { _, _, _ -> loggedError.complete(Unit) }
        ) {
            completed.complete(Unit)
        }.join()

        assertTrue(completed.isCompleted)
        assertFalse(loggedError.isCompleted)
    }

    @Test
    fun normalCompletionFinishesPendingResultExactlyOnce() = runBlocking {
        var finishCount = 0
        val completed = CompletableDeferred<Unit>()

        launchReceiverAsync(
            pending = null,
            tag = "test",
            message = "normal completion",
            scope = this,
            finishPendingResult = { finishCount++ }
        ) {
            completed.complete(Unit)
        }.join()

        assertTrue(completed.isCompleted)
        assertEquals(1, finishCount)
    }

    @Test
    fun timeoutLogsWarningAndFinishesPendingResultExactlyOnce() = runBlocking {
        var finishCount = 0
        val loggedWarning = CompletableDeferred<LoggedError>()

        val job = launchReceiverAsync(
            pending = null,
            tag = "NamazWidget",
            message = "widget update failed",
            scope = this,
            timeoutMs = 25,
            logWarning = { tag, message, error ->
                loggedWarning.complete(LoggedError(tag, message, error))
            },
            finishPendingResult = { finishCount++ }
        ) {
            awaitCancellation()
        }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, finishCount)
        val warning = loggedWarning.await()
        assertEquals("NamazWidget", warning.tag)
        assertEquals("widget update failed (timed out after 25ms)", warning.message)
        assertTrue(warning.error is kotlinx.coroutines.TimeoutCancellationException)
    }

    @Test
    fun blockFailureIsLoggedWhenPendingResultIsNull() = runBlocking {
        val failure = IllegalStateException("widget update failed")
        val logged = CompletableDeferred<LoggedError>()

        launchReceiverAsync(
            pending = null,
            tag = "NamazWidget",
            message = "widget update failed",
            scope = this,
            logError = { tag, message, error -> logged.complete(LoggedError(tag, message, error)) }
        ) {
            throw failure
        }.join()

        assertTrue(logged.isCompleted)
        val entry = logged.await()
        assertEquals("NamazWidget", entry.tag)
        assertEquals("widget update failed", entry.message)
        assertTrue(entry.error is IllegalStateException)
        assertEquals(failure.message, entry.error.message)
    }

    @Test
    fun nonTimeoutCancellationIsRethrownAndFinishesPendingResult() = runBlocking {
        val cancellation = CancellationException("receiver scope cancelled")
        var finishCount = 0
        val completionCause = CompletableDeferred<Throwable?>()

        val job = launchReceiverAsync(
            pending = null,
            tag = "test",
            message = "cancelled",
            scope = this,
            finishPendingResult = { finishCount++ }
        ) {
            throw cancellation
        }
        job.invokeOnCompletion { completionCause.complete(it) }
        job.join()

        val completion = completionCause.await()
        assertTrue(completion is CancellationException)
        assertEquals(cancellation.message, completion?.message)
        assertEquals(1, finishCount)
    }

    private data class LoggedError(
        val tag: String,
        val message: String,
        val error: Throwable
    )
}
