package com.example.data.repository

import com.example.data.local.ShoppingDao
import com.example.data.local.ShoppingItem
import com.example.data.local.ShoppingSession
import kotlinx.coroutines.flow.Flow

class ShoppingRepository(private val dao: ShoppingDao) {

    val allSessions: Flow<List<ShoppingSession>> = dao.getAllSessions()
    val activeSession: Flow<ShoppingSession?> = dao.getActiveSession()

    fun getSessionById(sessionId: Long): Flow<ShoppingSession?> = dao.getSessionById(sessionId)

    fun getItemsForSession(sessionId: Long): Flow<List<ShoppingItem>> = dao.getItemsForSession(sessionId)

    fun getTotalSpentForSession(sessionId: Long): Flow<Double?> = dao.getTotalSpentForSession(sessionId)

    suspend fun createSession(
        title: String,
        threshold: Double,
        currencySymbol: String = "$",
        isShoppingList: Boolean = false
    ): Long {
        val session = ShoppingSession(
            title = title.ifBlank { "Shopping Trip" },
            threshold = threshold.coerceAtLeast(0.0),
            currencySymbol = currencySymbol,
            isShoppingList = isShoppingList
        )
        return dao.insertSession(session)
    }

    suspend fun toggleSessionCompletion(session: ShoppingSession): Boolean {
        val newStatus = !session.isCompleted
        val updated = session.copy(
            isCompleted = newStatus,
            completedAt = if (newStatus) System.currentTimeMillis() else null
        )
        dao.updateSession(updated)
        return newStatus
    }

    suspend fun updateSession(session: ShoppingSession) = dao.updateSession(session)

    suspend fun deleteSession(sessionId: Long) = dao.deleteSessionById(sessionId)

    suspend fun addItem(sessionId: Long, name: String, price: Double, quantity: Int = 1): Long {
        val item = ShoppingItem(
            sessionId = sessionId,
            name = name.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            price = price.coerceAtLeast(0.0),
            quantity = quantity.coerceAtLeast(1)
        )
        return dao.insertItem(item)
    }

    suspend fun updateItem(item: ShoppingItem) = dao.updateItem(item)

    suspend fun deleteItem(item: ShoppingItem) = dao.deleteItem(item)

    suspend fun deleteItemById(itemId: Long) = dao.deleteItemById(itemId)
}
