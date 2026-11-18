package com.accessoryTools.teleprompter.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialGateTest {
    @Test
    fun requiresTwoActionsBeforeTheFirstAd() {
        val gate = InterstitialGate(minActions = 2, minIntervalMs = 60_000L)

        assertFalse(gate.shouldShow(nowMs = 1_000L))
        assertTrue(gate.shouldShow(nowMs = 2_000L))
    }

    @Test
    fun resetsActionCountAfterAnAdIsShown() {
        val gate = InterstitialGate(minActions = 2, minIntervalMs = 60_000L)
        gate.shouldShow(nowMs = 1_000L)
        assertTrue(gate.shouldShow(nowMs = 2_000L))
        gate.onShown(nowMs = 2_000L)

        assertFalse(gate.shouldShow(nowMs = 3_000L))
        assertTrue(gate.shouldShow(nowMs = 70_000L))
    }

    @Test
    fun enforcesMinimumIntervalEvenAfterTwoActions() {
        val gate = InterstitialGate(minActions = 2, minIntervalMs = 60_000L)
        gate.shouldShow(nowMs = 1_000L)
        assertTrue(gate.shouldShow(nowMs = 2_000L))
        gate.onShown(nowMs = 2_000L)

        assertFalse(gate.shouldShow(nowMs = 20_000L))
        assertFalse(gate.shouldShow(nowMs = 30_000L))
        assertTrue(gate.shouldShow(nowMs = 62_000L))
    }
}
