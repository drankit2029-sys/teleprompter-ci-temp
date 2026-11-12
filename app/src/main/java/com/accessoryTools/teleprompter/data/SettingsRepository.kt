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

    val settings: Flow<AppSettings> =
        appContext.settingsDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                AppSettings(
                    speedWpm = preferences[Keys.SPEED_WPM] ?: 140f,
                    fontSizeSp = preferences[Keys.FONT_SIZE_SP] ?: 46f,
                    adaptiveContrast = preferences[Keys.ADAPTIVE_CONTRAST] ?: true,
                    sortOrder =
                        LibrarySort.entries.firstOrNull {
                            it.key == preferences[Keys.SORT_ORDER]
                        } ?: LibrarySort.UPDATED,
                    favoritesOnly = preferences[Keys.FAVORITES_ONLY] ?: false,
                )
            }

    fun savePrompterSettings(
        speedWpm: Float,
        fontSizeSp: Float,
        adaptiveContrast: Boolean,
    ) {
        prompterSaveJob?.cancel()
        prompterSaveJob =
            scope.launch {
                delay(250)
                appContext.settingsDataStore.edit { preferences ->
                    preferences[Keys.SPEED_WPM] = speedWpm
                    preferences[Keys.FONT_SIZE_SP] = fontSizeSp
                    preferences[Keys.ADAPTIVE_CONTRAST] = adaptiveContrast
                }
            }
    }

    fun saveLibraryPreferences(
        sortOrder: LibrarySort,
        favoritesOnly: Boolean,
    ) {
        scope.launch {
            appContext.settingsDataStore.edit { preferences ->
                preferences[Keys.SORT_ORDER] = sortOrder.key
                preferences[Keys.FAVORITES_ONLY] = favoritesOnly
            }
        }
    }

    private object Keys {
        val SPEED_WPM = floatPreferencesKey("speed_wpm")
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val ADAPTIVE_CONTRAST = booleanPreferencesKey("adaptive_contrast")
        val SORT_ORDER = stringPreferencesKey("library_sort_order")
        val FAVORITES_ONLY = booleanPreferencesKey("library_favorites_only")
    }
}
