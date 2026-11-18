package com.accessoryTools.teleprompter.ads

import com.accessoryTools.teleprompter.BuildConfig

/**
 * AdMob configuration. The IDs are injected at build time from the
 * TELEPROMPTER_APP_ID / TELEPROMPTER_BANNER / TELEPROMPTER_INTERSTITIAL
 * environment variables (production) or Google's official test units (debug).
 *
 * Before shipping with production IDs:
 * - Complete the UMP consent flow (handled by [ConsentManager]).
 * - Publish a privacy policy and complete the Play Data safety form.
 * - Never click or tap live ads during testing; use debug builds instead.
 */
object AdConfig {
    val APP_ID: String = BuildConfig.ADMOB_APP_ID
    val BANNER_ID: String = BuildConfig.ADMOB_BANNER_ID
    val INTERSTITIAL_ID: String = BuildConfig.ADMOB_INTERSTITIAL_ID

    /**
     * AdMob allows a 30-150 second banner refresh window. 60 seconds keeps a
     * comfortable margin above the floor and matches the recommended setting.
     */
    const val BANNER_ROTATION_MS = 60_000L

    /**
     * Google's interstitial policy allows at most one ad after every two user
     * actions, with no immediate repeat after a dismiss. Stage exits are the
     * only action counted here, which is stricter than the policy requires.
     */
    const val INTERSTITIAL_MIN_ACTIONS = 2
    const val INTERSTITIAL_MIN_INTERVAL_MS = 60_000L
}
