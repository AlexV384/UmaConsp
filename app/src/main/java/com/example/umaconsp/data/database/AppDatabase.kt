package com.example.umaconsp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * База данных Room для приложения.
 * Содержит две таблицы: chats и messages.
 */
@Database(
    entities = [ChatEntity::class, MessageEntity::class], // сущности, которые будут сохранены
    version = 3,                                           // текущая версия схемы БД
    exportSchema = false                                   // отключаем экспорт схемы (для упрощения)
)
abstract class AppDatabase : RoomDatabase() {

    // Абстрактные методы доступа к данным (DAO)
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        // Аннотация @Volatile гарантирует видимость изменений INSTANCE между потоками
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Миграция с версии 2 на версию 3.
         * Добавляет колонку `thinking` в таблицу `messages`.
         * Колонка может быть NULL (старые сообщения останутся без размышлений).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Выполняем SQL-запрос для добавления колонки
                database.execSQL("ALTER TABLE messages ADD COLUMN thinking TEXT")
            }
        }

        /**
         * Получить синглтон базы данных.
         * Реализован потокобезопасно с двойной проверкой блокировки (double-checked locking).
         * @param context Контекст приложения (используется applicationContext, чтобы избежать утечек)
         * @return Экземпляр AppDatabase
         */
        fun getDatabase(context: Context): AppDatabase {
            // Первая проверка (без блокировки) — быстрый путь, если INSTANCE уже создан
            return INSTANCE ?: synchronized(this) {
                // Вторая проверка внутри synchronized — гарантирует, что создание произойдёт только один раз
                val instance = INSTANCE
                if (instance != null) {
                    instance
                } else {
                    // Создаём билдер базы данных
                    Room.databaseBuilder(
                        context.applicationContext,          // используем applicationContext, чтобы не зависеть от Activity
                        AppDatabase::class.java,             // класс базы данных
                        "umaconsp_db"                        // имя файла базы данных
                    )
                        .addMigrations(MIGRATION_2_3)        // добавляем миграцию 2→3
                        .build()                             // строим базу
                        .also { INSTANCE = it }              // сохраняем в статическую переменную
                }
            }
        }
    }
}