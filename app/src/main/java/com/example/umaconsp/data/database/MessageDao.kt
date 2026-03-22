package com.example.umaconsp.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) для работы с таблицей сообщений.
 * Обеспечивает операции CRUD (Create, Read, Update, Delete) для сообщений.
 * Все методы, возвращающие Flow, автоматически обновляют результаты при изменениях в БД.
 * Suspend-функции предназначены для вызова из корутин.
 */
@Dao
interface MessageDao {

    /**
     * Получить все сообщения указанного чата, отсортированные по времени создания (от старых к новым).
     *
     * @param chatId Идентификатор чата
     * @return Flow<List<MessageEntity>> — поток, который эмитит новый список при каждом изменении
     *         в таблице messages для данного chatId. Благодаря Flow UI автоматически обновляется
     *         при добавлении, удалении или обновлении сообщений.
     *
     * Сортировка ASC (по возрастанию) нужна для правильного порядка отображения сообщений в чате.
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    /**
     * Найти сообщение по его уникальному идентификатору.
     *
     * @param messageId Уникальный идентификатор сообщения (String)
     * @return MessageEntity? — объект сообщения или null, если сообщение не найдено
     *
     * Функция suspend, выполняется в корутине и не блокирует основной поток.
     * Используется при обновлении сообщения (например, для потокового вывода).
     */
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    /**
     * Вставить новое сообщение в базу данных.
     *
     * @param message Объект MessageEntity для вставки
     *
     * @Insert(onConflict = OnConflictStrategy.REPLACE) — если сообщение с таким же id уже существует,
     * старая запись будет заменена новой. Это полезно при обновлении сообщений во время потокового вывода.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    /**
     * Обновить существующее сообщение.
     *
     * @param message Объект MessageEntity с изменёнными полями. Должен содержать корректный id.
     *
     * @Update выполняет обновление на основе первичного ключа (id).
     * Используется для постепенного обновления текста сообщения при потоковом выводе.
     */
    @Update
    suspend fun updateMessage(message: MessageEntity)

    /**
     * Удалить все сообщения указанного чата.
     *
     * @param chatId Идентификатор чата
     *
     * Этот метод вызывается при удалении чата (хотя внешний ключ в MessageEntity настроен
     * с ON DELETE CASCADE, так что удаление чата автоматически удаляет сообщения).
     * Оставлен для явного удаления в случае необходимости.
     */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)
}