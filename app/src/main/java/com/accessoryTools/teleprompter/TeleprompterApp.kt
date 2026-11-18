package com.accessoryTools.teleprompter

import android.app.Application
import android.util.Log
import com.accessoryTools.teleprompter.ads.AdsInitialization
import com.accessoryTools.teleprompter.ads.InterstitialAds
import com.accessoryTools.teleprompter.data.ScriptRepository
import com.accessoryTools.teleprompter.data.ScriptSharing
import com.accessoryTools.teleprompter.data.SettingsRepository
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.prompter.StageTypefaces
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TeleprompterApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        const val TAG = "TeleprompterApp"
    }

    lateinit var scripts: ScriptRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        StageTypefaces.init(this)
        scripts = ScriptRepository(this, applicationScope)
        settings = SettingsRepository(this, applicationScope)
        // AdMob initialization happens in MainActivity after the UMP consent
        // flow resolves (ConsentManager); ads must never be requested before
        // consent is established.
        PrompterSession.onPrompterSettingsChanged = settings::savePrompterSettings
        PrompterSession.onOverlayAreaChanged = settings::saveOverlayArea
        applicationScope.launch {
            ScriptSharing.clearExports(this@TeleprompterApp)
            val stored = settings.settings.first()
            withContext(Dispatchers.Main.immediate) {
                PrompterSession.applyPersistedSettings(
                    speedWpm = stored.speedWpm,
                    fontSizeSp = stored.fontSizeSp,
                    adaptiveContrast = stored.adaptiveContrast,
                )
                PrompterSession.applyOverlayArea(stored.overlayArea)
            }
            runCatching { scripts.seedDemoOnce("Demo script", PrompterSession.demoScript) }
                .onFailure { Log.w(TAG, "Demo seed failed", it) }
        }
    }
}
