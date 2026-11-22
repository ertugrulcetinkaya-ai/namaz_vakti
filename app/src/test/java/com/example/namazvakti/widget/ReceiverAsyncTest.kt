package com.example.namazvakti.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
            logError = { _, _, _ -> loggedError.complete(Unit) }
        ) {
            completed.complete(Unit)
        }.join()

        assertTrue(completed.isCompleted)
        assertFalse(loggedError.isCompleted)
    }

    @Test
    fun blockFailureIsLoggedWhenPendingResultIsNull() = runBlocking {
        val failure = IllegalStateException("widget update failed")
        val logged = CompletableDeferred<LoggedError>()

        launchReceiverAsync(
            pending = null,
            tag = "NamazWidget",
            message = "widget update failed",
            logError = { tag, message, error -> logged.complete(LoggedError(tag, message, error)) }
        ) {
            throw failure
        }.join()

        assertTrue(logged.isCompleted)
        val entry = logged.await()
        assertEquals("NamazWidget", entry.tag)
        assertEquals("widget update failed", entry.message)
        assertSame(failure, entry.error)
    }

    private data class LoggedError(
        val tag: String,
        val message: String,
        val error: Throwable
    )
}
