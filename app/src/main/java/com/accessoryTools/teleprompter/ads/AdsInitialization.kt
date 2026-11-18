package com.accessoryTools.teleprompter.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether the AdMob SDK has finished initializing and whether the UMP
 * consent flow has resolved. Banner composables wait for both before requesting
 * an ad so the first launch never races initialization or consent.
 */
object AdsInitialization {
    var ready by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    /** True once the UMP consent flow has completed (or was not required). */
    var consentReady by mutableStateOf(false)
        private set

    fun markReady() {
        ready = true
    }

    fun markFailed() {
        failed = true
    }

    fun markConsentReady() {
        consentReady = true
    }
}
