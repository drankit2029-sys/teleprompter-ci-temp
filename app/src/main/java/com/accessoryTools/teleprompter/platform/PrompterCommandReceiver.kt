package com.accessoryTools.teleprompter.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.accessoryTools.teleprompter.prompter.PrompterSession

class PrompterCommandReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_TOGGLE_PLAY -> {
                PrompterSession.togglePlay()
            }

            ACTION_RESTART -> {
                PrompterSession.restartAndPlay()
            }

            ACTION_SPEED_UP -> {
                PrompterSession.nudgeSpeed(SPEED_STEP)
            }

            ACTION_SPEED_DOWN -> {
                PrompterSession.nudgeSpeed(-SPEED_STEP)
            }

            ACTION_CYCLE_PIP_SIZE -> {
                Log.d("TeleprompterPip", "Resize action received")
                context.sendBroadcast(
                    Intent(ACTION_APPLY_PIP_RESIZE).setPackage(context.packageName),
                )
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY = "com.accessoryTools.teleprompter.action.TOGGLE_PLAY"
        const val ACTION_RESTART = "com.accessoryTools.teleprompter.action.RESTART"
        const val ACTION_SPEED_UP = "com.accessoryTools.teleprompter.action.SPEED_UP"
        const val ACTION_SPEED_DOWN = "com.accessoryTools.teleprompter.action.SPEED_DOWN"
        const val ACTION_CYCLE_PIP_SIZE = "com.accessoryTools.teleprompter.action.CYCLE_PIP_SIZE"
        const val ACTION_APPLY_PIP_RESIZE = "com.accessoryTools.teleprompter.action.APPLY_PIP_RESIZE"
        const val SPEED_STEP = 10f
    }
}
