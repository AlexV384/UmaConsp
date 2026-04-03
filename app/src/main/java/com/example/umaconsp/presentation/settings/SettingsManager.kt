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
        private val SERVER_IP_KEY = stringPreferencesKey("server_ip")
        private val USE_LOCAL_MODEL_KEY = booleanPreferencesKey("use_local_model")
        private val EXPORT_FOLDER_URI_KEY = stringPreferencesKey("export_folder_uri")
        const val DEFAULT_IP = "192.168.1.67"
    }

    val serverIpFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[SERVER_IP_KEY] ?: DEFAULT_IP
    }

    suspend fun setServerIp(ip: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_IP_KEY] = ip
        }
    }

    val useLocalModelFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_LOCAL_MODEL_KEY] ?: true
    }

    suspend fun setUseLocalModel(useLocal: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_LOCAL_MODEL_KEY] = useLocal
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