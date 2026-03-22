package com.example.umaconsp.presentation.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Расширение Context для получения экземпляра DataStore с именем "settings".
 * DataStore — современная замена SharedPreferences, работает с корутинами и Flow.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Менеджер настроек приложения, использующий DataStore Preferences.
 * Хранит IP-адрес сервера и предоставляет Flow для наблюдения за изменениями.
 *
 * @param context Контекст приложения (обычно Application или Activity)
 */
class SettingsManager(context: Context) {
    // Экземпляр DataStore, полученный через расширение
    private val dataStore = context.dataStore

    companion object {
        // Ключ для хранения IP-адреса в DataStore
        private val SERVER_IP_KEY = stringPreferencesKey("server_ip")

        // IP-адрес по умолчанию (используется при первом запуске)
        const val DEFAULT_IP = "192.168.1.67"
    }

    /**
     * Поток (Flow) текущего IP-адреса.
     * При изменении значения в DataStore, Flow автоматически эмитит новое значение,
     * что позволяет UI реагировать на изменения в реальном времени.
     */
    val serverIpFlow: Flow<String> = dataStore.data.map { preferences ->
        // Если ключ отсутствует, возвращаем значение по умолчанию
        preferences[SERVER_IP_KEY] ?: DEFAULT_IP
    }

    /**
     * Сохраняет новый IP-адрес в DataStore.
     * Функция suspend — вызывается из корутины.
     *
     * @param ip Новый IP-адрес (например, "192.168.1.100")
     */
    suspend fun setServerIp(ip: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_IP_KEY] = ip
        }
    }
}