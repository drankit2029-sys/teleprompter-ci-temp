package com.accessoryTools.teleprompter.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.accessoryTools.teleprompter.BuildConfig

/**
 * Preloaded interstitial used only when the user leaves a script. It is
 * skipped while a recording-like session (PiP, transparent overlay) is active
 * and gated by [InterstitialGate] so it appears before the library break page,
 * never immediately after another interstitial, and never after only one
 * action.
 */
object InterstitialAds {
    private const val TAG = "InterstitialAds"
    private var loaded: InterstitialAd? = null
    private var loading = false
    private val gate =
        InterstitialGate(
            minActions = AdConfig.INTERSTITIAL_MIN_ACTIONS,
            minIntervalMs = AdConfig.INTERSTITIAL_MIN_INTERVAL_MS,
        )

    fun preload(context: Context) {
        if (BuildConfig.AD_FREE || !AdsInitialization.consentReady) {
            return
        }
        if (loaded != null || loading) {
            return
        }
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            AdConfig.INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    loaded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    loaded = null
                    if (BuildConfig.DEBUG) Log.d(TAG, "Interstitial failed to load: ${error.message}")
                }
            },
        )
    }

    /**
     * Attempts to show an interstitial before leaving the script. [onFinished]
     * always runs, including when no ad is shown, so callers can navigate
     * without ever blocking the user.
     */
    fun maybeShow(
        activity: Activity,
        onFinished: () -> Unit,
    ) {
        if (BuildConfig.AD_FREE || !AdsInitialization.consentReady) {
            onFinished()
            return
        }
        val eligible =
            !activity.isFinishing &&
                !activity.isDestroyed &&
                (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false &&
                !activity.isInPictureInPictureMode &&
                !PrompterSession.overlayRunning &&
                !PrompterSession.locked
        if (!eligible) {
            onFinished()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!gate.shouldShow(now)) {
            onFinished()
            preload(activity)
            return
        }
        val ad = loaded
        if (ad == null) {
            onFinished()
            preload(activity)
            return
        }
        loaded = null
        var finished = false
        val finish = { shown: Boolean ->
            if (!finished) {
                finished = true
                if (shown) {
                    gate.onShown(SystemClock.elapsedRealtime())
                }
                preload(activity)
                onFinished()
            }
        }
        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    finish(true)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Interstitial failed to show: ${error.message}")
                    finish(false)
                }
            }
        runCatching { ad.show(activity) }
            .onFailure { error ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Interstitial show threw: ${error.message}")
                finish(false)
            }
    }
}
