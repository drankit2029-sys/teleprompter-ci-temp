package com.accessoryTools.teleprompter.ads

/**
 * Pure policy gate for interstitials.
 *
 * Google's policy allows at most one interstitial after every two user actions
 * and forbids showing one immediately after another. Teleprompter counts a
 * qualifying stage exit as one action, so this gate is deliberately stricter
 * than a plain time throttle.
 */
internal class InterstitialGate(
    private val minActions: Int = 2,
    private val minIntervalMs: Long = 60_000L,
) {
    private var actionsSinceShow = 0
    private var lastShownAt: Long? = null

    /** Records a qualifying action and returns true when an ad may be shown. */
    fun shouldShow(nowMs: Long): Boolean {
        actionsSinceShow += 1
        if (actionsSinceShow < minActions) {
            return false
        }
        val last = lastShownAt
        if (last != null && nowMs - last < minIntervalMs) {
            return false
        }
        return true
    }

    /** Resets the action counter and starts the minimum-interval window. */
    fun onShown(nowMs: Long) {
        actionsSinceShow = 0
        lastShownAt = nowMs
    }
}
