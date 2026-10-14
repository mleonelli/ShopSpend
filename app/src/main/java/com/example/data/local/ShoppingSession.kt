package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_sessions")
data class ShoppingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val threshold: Double = 0.0, // 0.0 means no threshold limit
    val currencySymbol: String = "$",
    val createdAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val isShoppingList: Boolean = false
)
