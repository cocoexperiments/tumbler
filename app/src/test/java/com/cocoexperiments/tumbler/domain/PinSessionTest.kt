package com.cocoexperiments.tumbler.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSessionTest {
    private fun group(vararg d: Int) = KeypadGroup(d.toList())

    @Test
    fun correctSequenceCompletes() {
        val session = PinSession(listOf(1, 2, 3, 4))
        assertEquals(TapResult.Advanced(1), session.onGroupSelected(group(1, 8, 9)))
        assertEquals(TapResult.Advanced(2), session.onGroupSelected(group(0, 2, 5)))
        assertEquals(TapResult.Advanced(3), session.onGroupSelected(group(3, 6, 7)))
        assertEquals(TapResult.Completed, session.onGroupSelected(group(4, 5, 6)))
    }

    @Test
    fun wrongGroupResetsToStepZero() {
        val session = PinSession(listOf(1, 2, 3, 4))
        session.onGroupSelected(group(1, 8, 9))
        assertEquals(TapResult.Rejected, session.onGroupSelected(group(5, 6, 7)))
        assertEquals(0, session.step)
    }

    @Test
    fun lockoutEscalatesAndCaps() {
        assertEquals(0L, LockoutPolicy.lockoutMillis(4))
        assertEquals(30_000L, LockoutPolicy.lockoutMillis(5))
        assertEquals(60_000L, LockoutPolicy.lockoutMillis(6))
        assertTrue(LockoutPolicy.lockoutMillis(50) <= 15 * 60_000L)
    }
}
