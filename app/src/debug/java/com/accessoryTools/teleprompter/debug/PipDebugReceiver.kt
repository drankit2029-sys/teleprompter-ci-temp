package com.accessoryTools.teleprompter.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.accessoryTools.teleprompter.platform.PrompterCommandReceiver

/**
 * Debug-build-only hook for exercising PiP actions from adb. It is excluded
 * from release builds by the debug source set.
 */
class PipDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_CYCLE_PIP_SIZE) {
            return
        }
        Log.d(TAG, "Debug PiP resize requested")
        context.sendBroadcast(
            Intent(PrompterCommandReceiver.ACTION_APPLY_PIP_RESIZE)
                .setPackage(context.packageName),
        )
    }

    companion object {
        const val ACTION_CYCLE_PIP_SIZE = "com.accessoryTools.teleprompter.debug.CYCLE_PIP_SIZE"
        private const val TAG = "TeleprompterPip"
    }
}
