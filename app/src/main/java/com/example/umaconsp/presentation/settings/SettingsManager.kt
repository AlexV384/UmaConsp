package com.example.umaconsp.presentation.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val FIRST_LAUNCH_KEY = booleanPreferencesKey("first_launch")
        private val MODEL_MODE_KEY = stringPreferencesKey("model_mode")
        private val OCR_LANGUAGE_KEY = stringPreferencesKey("ocr_language")
        private val EXPORT_FOLDER_URI_KEY = stringPreferencesKey("export_folder_uri")

        const val DEFAULT_MODE = "tesseract"   // Tesseract
        const val DEFAULT_OCR_LANGUAGE = "rus"
    }

    // Первый запуск
    val isFirstLaunch: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[FIRST_LAUNCH_KEY] ?: true   // по умолчанию первый запуск
    }

    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { preferences ->
            preferences[FIRST_LAUNCH_KEY] = false
        }
    }

    // Режим распознавания
    val modelModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[MODEL_MODE_KEY] ?: DEFAULT_MODE
    }

    suspend fun setModelMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[MODEL_MODE_KEY] = mode
        }
    }

    val ocrLanguageFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[OCR_LANGUAGE_KEY] ?: DEFAULT_OCR_LANGUAGE
    }

    suspend fun setOcrLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[OCR_LANGUAGE_KEY] = language
        }
    }

    val exportFolderUriFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[EXPORT_FOLDER_URI_KEY]
    }

    suspend fun setExportFolderUri(uriString: String?) {
        dataStore.edit { preferences ->
            if (uriString == null) {
                preferences.remove(EXPORT_FOLDER_URI_KEY)
            } else {
                preferences[EXPORT_FOLDER_URI_KEY] = uriString
            }
        }
    }
}