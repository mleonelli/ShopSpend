package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_sessions ORDER BY isCompleted ASC, createdAt DESC")
    fun getAllSessions(): Flow<List<ShoppingSession>>

    @Query("SELECT * FROM shopping_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: Long): Flow<ShoppingSession?>

    @Query("SELECT * FROM shopping_sessions WHERE isCompleted = 0 ORDER BY createdAt DESC LIMIT 1")
    fun getActiveSession(): Flow<ShoppingSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ShoppingSession): Long

    @Update
    suspend fun updateSession(session: ShoppingSession)

    @Query("DELETE FROM shopping_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("SELECT * FROM shopping_items WHERE sessionId = :sessionId ORDER BY addedAt DESC")
    fun getItemsForSession(sessionId: Long): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): ShoppingItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem): Long

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)

    @Query("DELETE FROM shopping_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("SELECT SUM(price * quantity) FROM shopping_items WHERE sessionId = :sessionId")
    fun getTotalSpentForSession(sessionId: Long): Flow<Double?>
}
