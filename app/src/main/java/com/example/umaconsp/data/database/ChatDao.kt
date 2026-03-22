package com.example.umaconsp.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) для работы с таблицей чатов.
 * Обеспечивает операции CRUD (Create, Read, Update, Delete) с использованием Room.
 * Все методы, возвращающие Flow, автоматически обновляют результаты при изменениях в БД.
 * Suspend-функции предназначены для вызова из корутин.
 */
@Dao
interface ChatDao {

    /**
     * Получить список всех чатов, отсортированных по времени последнего сообщения (от новых к старым).
     *
     * @return Flow<List<ChatEntity>> — поток, который эмитит новый список при каждом изменении в таблице chats.
     *         Благодаря Flow, UI может автоматически обновляться при добавлении, удалении или обновлении чатов.
     */
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    /**
     * Найти чат по его уникальному идентификатору.
     * Возвращает null, если чат с таким id не существует.
     *
     * @param chatId Уникальный идентификатор чата (String)
     * @return ChatEntity? — объект чата или null
     *
     * Функция suspend, выполняется в корутине и не блокирует основной поток.
     */
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    /**
     * Вставить новый чат в базу данных.
     *
     * @param chat Объект ChatEntity для вставки
     *
     * @Insert(onConflict = OnConflictStrategy.REPLACE) — в случае конфликта (чат с таким же id уже существует),
     * старая запись будет заменена новой.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    /**
     * Обновить существующий чат.
     *
     * @param chat Объект ChatEntity с изменёнными полями. Должен содержать корректный id.
     *
     * @Update выполняет обновление на основе первичного ключа (id).
     */
    @Update
    suspend fun updateChat(chat: ChatEntity)

    /**
     * Удалить чат из базы данных.
     *
     * @param chat Объект ChatEntity, который нужно удалить. Удаление происходит по id.
     *             Внешний ключ в таблице messages настроен с ON DELETE CASCADE,
     *             поэтому все сообщения этого чата будут удалены автоматически.
     */
    @Delete
    suspend fun deleteChat(chat: ChatEntity)
}