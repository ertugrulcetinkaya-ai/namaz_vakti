package com.example.namazvakti.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulingTest {
    @Test
    fun exactAlarmSuccessReturnsTrue() {
        var exactCalls = 0
        var inexactCalls = 0

        val scheduledExact = AlarmScheduling.set(
            exact = true,
            setExact = { exactCalls++ },
            setInexact = { inexactCalls++ },
            logWarning = { error("No warning expected") }
        )

        assertTrue(scheduledExact)
        assertEquals(1, exactCalls)
        assertEquals(0, inexactCalls)
    }

    @Test
    fun securityExceptionFallsBackAndReturnsFalse() {
        val denied = SecurityException("exact alarm denied")
        var inexactCalls = 0
        var logged: SecurityException? = null

        val scheduledExact = AlarmScheduling.set(
            exact = true,
            setExact = { throw denied },
            setInexact = { inexactCalls++ },
            logWarning = { logged = it }
        )

        assertFalse(scheduledExact)
        assertEquals(1, inexactCalls)
        assertSame(denied, logged)
    }

    @Test
    fun inexactAlarmSkipsExactCallAndReturnsFalse() {
        var exactCalls = 0
        var inexactCalls = 0

        val scheduledExact = AlarmScheduling.set(
            exact = false,
            setExact = { exactCalls++ },
            setInexact = { inexactCalls++ },
            logWarning = { error("No warning expected") }
        )

        assertFalse(scheduledExact)
        assertEquals(0, exactCalls)
        assertEquals(1, inexactCalls)
    }
}
