package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ShoppingItem
import com.example.data.local.ShoppingSession
import com.example.data.repository.ShoppingRepository
import com.example.util.ParsedVoiceItem
import com.example.util.PreferenceManager
import com.example.util.VibrationHelper
import com.example.util.VoiceParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiAlert {
    data class ThresholdExceeded(
        val totalSpent: Double,
        val threshold: Double,
        val exceededBy: Double,
        val currencySymbol: String
    ) : UiAlert()

    data class ItemAdded(
        val item: ShoppingItem,
        val currencySymbol: String
    ) : UiAlert()

    data class Message(val text: String) : UiAlert()
}

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ShoppingRepository
    private val prefManager = PreferenceManager.getInstance(application)

    init {
        val db = AppDatabase.getInstance(application)
        repository = ShoppingRepository(db.shoppingDao())
    }

    val isPremium: StateFlow<Boolean> = prefManager.isPremium
    val allSessions: StateFlow<List<ShoppingSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected / active session ID
    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    val currentSession: StateFlow<ShoppingSession?> = combine(
        _selectedSessionId,
        repository.allSessions
    ) { selectedId, sessions ->
        if (selectedId != null) {
            sessions.find { it.id == selectedId }
        } else {
            sessions.firstOrNull { !it.isCompleted } ?: sessions.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentItems: StateFlow<List<ShoppingItem>> = currentSession.flatMapLatest { session ->
        if (session != null) repository.getItemsForSession(session.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpent: StateFlow<Double> = currentItems.combine(flowOf(Unit)) { items, _ ->
        items.sumOf { it.price * it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Alert events
    private val _uiAlerts = MutableSharedFlow<UiAlert>()
    val uiAlerts: SharedFlow<UiAlert> = _uiAlerts.asSharedFlow()

    // Last recorded state of threshold violation to avoid spamming alerts on every recomposition
    private var lastNotifiedOverThresholdSessionId: Long? = null
    private var lastNotifiedOverThreshold = false

    fun selectSession(sessionId: Long) {
        _selectedSessionId.value = sessionId
        lastNotifiedOverThreshold = false
        lastNotifiedOverThresholdSessionId = null
    }

    fun createNewShopping(title: String, threshold: Double, currency: String = "$") {
        viewModelScope.launch {
            val newId = repository.createSession(title, threshold, currency)
            _selectedSessionId.value = newId
            lastNotifiedOverThreshold = false
            lastNotifiedOverThresholdSessionId = newId
            _uiAlerts.emit(UiAlert.Message("Started new shopping trip"))
        }
    }

    fun completeSession(sessionId: Long) {
        viewModelScope.launch {
            val session = allSessions.value.find { it.id == sessionId }
            if (session != null) {
                repository.updateSession(session.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
                _uiAlerts.emit(UiAlert.Message("Shopping trip marked as completed!"))
            }
        }
    }

    fun reopenSession(sessionId: Long) {
        viewModelScope.launch {
            val session = allSessions.value.find { it.id == sessionId }
            if (session != null) {
                repository.updateSession(session.copy(isCompleted = false, completedAt = null))
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
            }
            _uiAlerts.emit(UiAlert.Message("Shopping trip deleted"))
        }
    }

    fun updateThreshold(sessionId: Long, newThreshold: Double) {
        viewModelScope.launch {
            val session = allSessions.value.find { it.id == sessionId }
            if (session != null) {
                repository.updateSession(session.copy(threshold = newThreshold.coerceAtLeast(0.0)))
                checkThreshold(session.copy(threshold = newThreshold.coerceAtLeast(0.0)), totalSpent.value)
            }
        }
    }

    fun addItem(name: String, price: Double, quantity: Int = 1) {
        viewModelScope.launch {
            val session = currentSession.value ?: run {
                // Automatically create initial session if none exists
                val newId = repository.createSession("Shopping Trip", 50.0, "$")
                _selectedSessionId.value = newId
                repository.getSessionById(newId)
                allSessions.value.find { it.id == newId } ?: ShoppingSession(id = newId, title = "Shopping Trip", threshold = 50.0)
            }

            val addedItemId = repository.addItem(session.id, name, price, quantity)
            val addedItem = ShoppingItem(
                id = addedItemId,
                sessionId = session.id,
                name = name,
                price = price,
                quantity = quantity
            )
            _uiAlerts.emit(UiAlert.ItemAdded(addedItem, session.currencySymbol))

            // Check threshold condition with newly added item
            val newTotal = totalSpent.value + (price * quantity)
            checkThreshold(session, newTotal)
        }
    }

    fun updateItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.updateItem(item)
            val session = currentSession.value
            if (session != null) {
                // Re-evaluate threshold after price or quantity edit
                val itemsWithoutThis = currentItems.value.filter { it.id != item.id }
                val newTotal = itemsWithoutThis.sumOf { it.price * it.quantity } + (item.price * item.quantity)
                checkThreshold(session, newTotal)
            }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            _uiAlerts.emit(UiAlert.Message("Removed ${item.name}"))
            val session = currentSession.value
            if (session != null) {
                val newTotal = (totalSpent.value - (item.price * item.quantity)).coerceAtLeast(0.0)
                if (session.threshold > 0 && newTotal <= session.threshold) {
                    lastNotifiedOverThreshold = false
                }
            }
        }
    }

    fun processVoiceInput(spokenText: String): ParsedVoiceItem? {
        val parsed = VoiceParser.parse(spokenText) ?: return null
        addItem(parsed.name, parsed.price, parsed.quantity)
        return parsed
    }

    private suspend fun checkThreshold(session: ShoppingSession, total: Double) {
        if (session.threshold > 0.0) {
            val wasOver = lastNotifiedOverThreshold && lastNotifiedOverThresholdSessionId == session.id
            val isNowOver = total > session.threshold

            if (isNowOver && !wasOver) {
                lastNotifiedOverThreshold = true
                lastNotifiedOverThresholdSessionId = session.id
                // Trigger haptic vibration alert
                VibrationHelper.vibrateThresholdAlert(getApplication())
                // Trigger alert event
                _uiAlerts.emit(
                    UiAlert.ThresholdExceeded(
                        totalSpent = total,
                        threshold = session.threshold,
                        exceededBy = total - session.threshold,
                        currencySymbol = session.currencySymbol
                    )
                )
            } else if (!isNowOver) {
                lastNotifiedOverThreshold = false
            }
        }
    }

    fun setPremium(premium: Boolean) {
        prefManager.setPremium(premium)
    }
}
