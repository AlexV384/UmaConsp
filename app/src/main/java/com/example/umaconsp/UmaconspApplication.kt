package com.example.umaconsp

import android.app.Application
import android.content.Context
import com.example.umaconsp.data.database.AppDatabase

/**
 * Класс приложения (Application), инициализируемый при запуске.
 * Используется как синглтон для доступа к контексту и базе данных из любого места приложения.
 *
 * Обязательно должен быть указан в AndroidManifest.xml в атрибуте android:name.
 */
class UmaconspApplication : Application() {

    companion object {
        /**
         * Ссылка на экземпляр приложения (синглтон).
         * Используется для получения контекста там, где он не доступен напрямую
         * (например, в ViewModel для доступа к строковым ресурсам).
         *
         * @throws UninitializedPropertyAccessException если обращение до вызова onCreate()
         */
        lateinit var instance: UmaconspApplication

        /**
         * Экземпляр базы данных Room (ленивая инициализация).
         * База данных создаётся при первом обращении к свойству.
         * Использует instance, который уже проинициализирован в onCreate().
         */
        val database: AppDatabase by lazy { AppDatabase.getDatabase(instance) }
    }

    /**
     * Вызывается при создании приложения (до запуска любой Activity).
     * Сохраняет ссылку на себя для последующего использования.
     */
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    /**
     * Возвращает контекст приложения.
     * Удобно для получения ресурсов в местах, где нет прямого доступа к Context,
     * например, в ViewModel.
     *
     * @return Application Context (глобальный контекст приложения)
     */
    fun getAppContext(): Context = applicationContext
}