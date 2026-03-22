package com.example.umaconsp.presentation.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Расширение Context для получения экземпляра DataStore с именем "theme_settings".
 * DataStore — современная замена SharedPreferences, работает с корутинами и Flow.
 * Данные хранятся в файле theme_settings.preferences_pb в каталоге приложения.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

/**
 * Менеджер темы приложения, использующий DataStore Preferences.
 * Хранит флаг тёмной темы и предоставляет Flow для наблюдения за изменениями.
 *
 * @param context Контекст приложения (обычно Application или Activity)
 */
class ThemeManager(context: Context) {
    // Экземпляр DataStore, полученный через расширение
    private val dataStore = context.dataStore

    companion object {
        // Ключ для хранения состояния тёмной темы (true — включена)
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    }

    /**
     * Поток (Flow) текущего состояния тёмной темы.
     * При изменении значения в DataStore, Flow автоматически эмитит новое значение,
     * что позволяет UI реагировать на изменения в реальном времени (например,
     * перерисовывать экран с новой темой).
     *
     * Значение по умолчанию: false (светлая тема).
     */
    val isDarkTheme: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

    /**
     * Сохраняет новое состояние тёмной темы в DataStore.
     * Функция suspend — вызывается из корутины.
     *
     * @param enabled true — включить тёмную тему, false — выключить
     */
    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }
}