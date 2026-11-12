package com.accessoryTools.teleprompter

import android.app.Application
import com.accessoryTools.teleprompter.data.ScriptRepository
import com.accessoryTools.teleprompter.data.SettingsRepository
import com.accessoryTools.teleprompter.prompter.PrompterSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TeleprompterApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var scripts: ScriptRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        scripts = ScriptRepository(this, applicationScope)
        settings = SettingsRepository(this, applicationScope)
        PrompterSession.onPrompterSettingsChanged = settings::savePrompterSettings
        applicationScope.launch {
            val stored = settings.settings.first()
            withContext(Dispatchers.Main.immediate) {
                PrompterSession.applyPersistedSettings(
                    speedWpm = stored.speedWpm,
                    fontSizeSp = stored.fontSizeSp,
                    adaptiveContrast = stored.adaptiveContrast,
                )
            }
            scripts.seedDemoOnce("Demo script", PrompterSession.demoScript)
        }
    }
}
