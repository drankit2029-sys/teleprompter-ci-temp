package com.accessoryTools.teleprompter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class LibrarySort(
    val key: String,
    val label: String,
) {
    UPDATED("updated", "Recently edited"),
    TITLE("title", "Title"),
    CREATED("created", "Newest first"),
}

data class AppSettings(
    val speedWpm: Float = 140f,
    val fontSizeSp: Float = 46f,
    val adaptiveContrast: Boolean = true,
    val sortOrder: LibrarySort = LibrarySort.UPDATED,
    val favoritesOnly: Boolean = false,
    val overlayArea: OverlayArea = OverlayArea.FULL,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "teleprompter_settings",
)

class SettingsRepository(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private var prompterSaveJob: Job? = null
    private var pendingPrompter: Triple<Float, Float, Boolean>? = null

    val settings: Flow<AppSettings> =
        appContext.settingsDataStore.data
            .catch { error ->
                android.util.Log.e("SettingsRepository", "DataStore unreadable; using defaults", error)
                emit(emptyPreferences())
            }
            .map { preferences ->
                AppSettings(
                    speedWpm = finiteIn(preferences[Keys.SPEED_WPM], 140f, 40f, 260f),
                    fontSizeSp = finiteIn(preferences[Keys.FONT_SIZE_SP], 46f, 18f, 100f),
                    adaptiveContrast = preferences[Keys.ADAPTIVE_CONTRAST] ?: true,
                    sortOrder =
                        LibrarySort.entries.firstOrNull {
                            it.key == preferences[Keys.SORT_ORDER]
                        } ?: LibrarySort.UPDATED,
                    favoritesOnly = preferences[Keys.FAVORITES_ONLY] ?: false,
                    overlayArea =
                        OverlayArea(
                            left = finiteIn(preferences[Keys.OVERLAY_LEFT], 0f, 0f, 1f),
                            top = finiteIn(preferences[Keys.OVERLAY_TOP], 0f, 0f, 1f),
                            right = finiteIn(preferences[Keys.OVERLAY_RIGHT], 1f, 0f, 1f),
                            bottom = finiteIn(preferences[Keys.OVERLAY_BOTTOM], 1f, 0f, 1f),
                        ).sanitized(),
                )
            }

    private fun finiteIn(
        value: Float?,
        default: Float,
        min: Float,
        max: Float,
    ): Float = if (value != null && value.isFinite()) value.coerceIn(min, max) else default

    fun savePrompterSettings(
        speedWpm: Float,
        fontSizeSp: Float,
        adaptiveContrast: Boolean,
    ) {
        pendingPrompter =
            Triple(
                finiteIn(speedWpm, 140f, 40f, 260f),
                finiteIn(fontSizeSp, 46f, 18f, 100f),
                adaptiveContrast,
            )
        prompterSaveJob?.cancel()
        prompterSaveJob =
            scope.launch {
                delay(250)
                writePendingPrompterNow()
            }
    }

    /**
     * Writes any pending prompter settings immediately. Call from ON_PAUSE/ON_STOP so a
     * process death never silently discards the last slider/toggle values.
     */
    fun flushPrompterSettings() {
        prompterSaveJob?.cancel()
        pendingPrompter ?: return
        scope.launch { writePendingPrompterNow() }
    }

    private suspend fun writePendingPrompterNow() {
        val pending = pendingPrompter ?: return
        pendingPrompter = null
        runCatching {
            appContext.settingsDataStore.edit { preferences ->
                preferences[Keys.SPEED_WPM] = pending.first
                preferences[Keys.FONT_SIZE_SP] = pending.second
                preferences[Keys.ADAPTIVE_CONTRAST] = pending.third
            }
        }.onFailure {
            android.util.Log.e("SettingsRepository", "DataStore write failed", it)
        }
    }

    fun saveLibraryPreferences(
        sortOrder: LibrarySort,
        favoritesOnly: Boolean,
    ) {
        scope.launch {
            runCatching {
                appContext.settingsDataStore.edit { preferences ->
                    preferences[Keys.SORT_ORDER] = sortOrder.key
                    preferences[Keys.FAVORITES_ONLY] = favoritesOnly
                }
            }.onFailure {
                android.util.Log.e("SettingsRepository", "DataStore write failed", it)
            }
        }
    }

    fun saveOverlayArea(area: OverlayArea) {
        val safe = area.sanitized()
        scope.launch {
            runCatching {
                appContext.settingsDataStore.edit { preferences ->
                    preferences[Keys.OVERLAY_LEFT] = safe.left
                    preferences[Keys.OVERLAY_TOP] = safe.top
                    preferences[Keys.OVERLAY_RIGHT] = safe.right
                    preferences[Keys.OVERLAY_BOTTOM] = safe.bottom
                }
            }.onFailure {
                android.util.Log.e("SettingsRepository", "DataStore write failed", it)
            }
        }
    }

    private object Keys {
        val SPEED_WPM = floatPreferencesKey("speed_wpm")
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val ADAPTIVE_CONTRAST = booleanPreferencesKey("adaptive_contrast")
        val SORT_ORDER = stringPreferencesKey("library_sort_order")
        val FAVORITES_ONLY = booleanPreferencesKey("library_favorites_only")
        val OVERLAY_LEFT = floatPreferencesKey("overlay_area_left")
        val OVERLAY_TOP = floatPreferencesKey("overlay_area_top")
        val OVERLAY_RIGHT = floatPreferencesKey("overlay_area_right")
        val OVERLAY_BOTTOM = floatPreferencesKey("overlay_area_bottom")
    }
}
