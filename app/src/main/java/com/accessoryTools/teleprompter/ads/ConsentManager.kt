package com.accessoryTools.teleprompter.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.accessoryTools.teleprompter.BuildConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google User Messaging Platform (UMP) consent flow.
 *
 * Required for EEA/UK/Switzerland (GDPR) and used for US state privacy
 * signals. Ads must not be requested until [AdsInitialization.consentReady]
 * is true; the banner/interstitial code gates on it.
 */
object ConsentManager {
    private const val TAG = "ConsentManager"
    private var started = false

    /** True when the user must be able to reopen the consent form (GDPR Art. 7(3)). */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    /**
     * Requests consent info and shows the consent form when required. Safe to
     * call on every activity creation; the network work only runs once.
     */
    fun gather(
        activity: Activity,
        onResolved: () -> Unit,
    ) {
        if (BuildConfig.AD_FREE) {
            AdsInitialization.markConsentReady()
            onResolved()
            return
        }
        if (started) {
            onResolved()
            return
        }
        started = true
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params =
            ConsentRequestParameters
                .Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form failed: ${formError.message}")
                    }
                    refreshPrivacyOptions(consentInformation)
                    if (consentInformation.canRequestAds()) {
                        AdsInitialization.markConsentReady()
                    }
                    onResolved()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                refreshPrivacyOptions(consentInformation)
                // Do not request ads without a resolved consent state.
                onResolved()
            },
        )
    }

    /** Reopens the UMP privacy options form (required entry point in Settings). */
    fun showPrivacyOptions(
        activity: Activity,
        onDismissed: () -> Unit = {},
    ) {
        if (BuildConfig.AD_FREE) {
            onDismissed()
            return
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form failed: ${formError.message}")
            }
            onDismissed()
        }
    }

    private fun refreshPrivacyOptions(consentInformation: ConsentInformation) {
        privacyOptionsRequired =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
