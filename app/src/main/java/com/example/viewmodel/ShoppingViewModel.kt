package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ShoppingItem
import com.example.data.local.ShoppingSession
import com.example.data.repository.ShoppingRepository
import com.example.util.PreferenceManager
import com.example.util.VibrationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExceededAlertInfo(
    val totalSpent: Double,
    val threshold: Double,
    val exceededBy: Double
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(
    private val repository: ShoppingRepository,
    private val preferenceManager: PreferenceManager,
    private val context: Context
) : ViewModel() {

    val allSessions: StateFlow<List<ShoppingSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSessionId = MutableStateFlow<Long?>(null)

    val currentSession: StateFlow<ShoppingSession?> = combine(
        allSessions,
        _selectedSessionId
    ) { sessions, selectedId ->
        if (sessions.isEmpty()) {
            null
        } else if (selectedId != null) {
            sessions.find { it.id == selectedId } ?: sessions.firstOrNull()
        } else {
            sessions.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val items: StateFlow<List<ShoppingItem>> = currentSession
        .flatMapLatest { session ->
            if (session != null) {
                repository.getItemsForSession(session.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpent: StateFlow<Double> = items.map { list ->
        list.sumOf { it.price * it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val isPremium: StateFlow<Boolean> = preferenceManager.isPremium
    val language: StateFlow<String> = preferenceManager.language
    val currency: StateFlow<String> = preferenceManager.currency
    val isVibrationEnabled: StateFlow<Boolean> = preferenceManager.isVibrationEnabled

    private val _showExceededAlert = MutableStateFlow(false)
    val showExceededAlert: StateFlow<Boolean> = _showExceededAlert.asStateFlow()

    private val _exceededAlertInfo = MutableStateFlow<ExceededAlertInfo?>(null)
    val exceededAlertInfo: StateFlow<ExceededAlertInfo?> = _exceededAlertInfo.asStateFlow()

    // Track whether we've already alerted for this session so we don't spam on every item
    private var lastAlertedSessionId: Long? = null
    private var lastAlertedTotal: Double = 0.0

    init {
        // Ensure at least one default shopping session exists on first start
        viewModelScope.launch {
            repository.allSessions.collect { sessions ->
                if (sessions.isEmpty()) {
                    val defaultTitle = if (preferenceManager.getEffectiveLanguage() == PreferenceManager.LANG_IT) "Spesa" else "Grocery Run"
                    val newId = repository.createSession(
                        title = defaultTitle,
                        threshold = 50.0,
                        currencySymbol = preferenceManager.currency.value
                    )
                    _selectedSessionId.value = newId
                }
            }
        }

        // Monitor budget threshold crossing
        viewModelScope.launch {
            combine(currentSession, totalSpent) { session, spent ->
                Pair(session, spent)
            }.collect { (session, spent) ->
                if (session != null && session.threshold > 0.0) {
                    if (spent > session.threshold) {
                        // Check if we haven't alerted yet for this overage state
                        val needsAlert = lastAlertedSessionId != session.id || lastAlertedTotal <= session.threshold
                        if (needsAlert && !_showExceededAlert.value) {
                            lastAlertedSessionId = session.id
                            lastAlertedTotal = spent
                            _exceededAlertInfo.value = ExceededAlertInfo(
                                totalSpent = spent,
                                threshold = session.threshold,
                                exceededBy = spent - session.threshold
                            )
                            _showExceededAlert.value = true
                            if (preferenceManager.isVibrationEnabled.value) {
                                VibrationHelper.vibrateThresholdAlert(context)
                            }
                        }
                    } else {
                        // Reset alert trigger if spent goes back below threshold
                        if (lastAlertedSessionId == session.id) {
                            lastAlertedTotal = spent
                        }
                    }
                }
            }
        }
    }

    fun addItem(name: String, price: Double, quantity: Int = 1) {
        viewModelScope.launch {
            val session = currentSession.value
            val targetSessionId = if (session != null) {
                session.id
            } else {
                val newId = repository.createSession(
                    title = "Shopping Trip",
                    threshold = 50.0,
                    currencySymbol = preferenceManager.currency.value
                )
                _selectedSessionId.value = newId
                newId
            }
            repository.addItem(targetSessionId, name, price, quantity)
        }
    }

    fun updateItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.updateItem(item)
        }
    }

    fun updateQuantity(item: ShoppingItem, newQuantity: Int) {
        viewModelScope.launch {
            if (newQuantity <= 0) {
                repository.deleteItem(item)
            } else {
                repository.updateItem(item.copy(quantity = newQuantity))
            }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun updateThreshold(newThreshold: Double) {
        viewModelScope.launch {
            currentSession.value?.let { session ->
                repository.updateSession(session.copy(threshold = newThreshold.coerceAtLeast(0.0)))
                // Reset alert status for new threshold
                lastAlertedTotal = 0.0
            }
        }
    }

    fun createNewSession(title: String, threshold: Double, currency: String) {
        viewModelScope.launch {
            preferenceManager.setCurrency(currency)
            val newId = repository.createSession(title, threshold, currency)
            _selectedSessionId.value = newId
            lastAlertedSessionId = null
            lastAlertedTotal = 0.0
        }
    }

    fun switchToSession(sessionId: Long) {
        _selectedSessionId.value = sessionId
        lastAlertedSessionId = null
        lastAlertedTotal = 0.0
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
            }
        }
    }

    fun setPremium(premium: Boolean) {
        preferenceManager.setPremium(premium)
    }

    fun setLanguage(lang: String) {
        preferenceManager.setLanguage(lang)
    }

    fun setCurrency(symbol: String) {
        preferenceManager.setCurrency(symbol)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        preferenceManager.setVibrationEnabled(enabled)
    }

    fun dismissExceededAlert() {
        _showExceededAlert.value = false
    }
}

class ShoppingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
            val database = AppDatabase.getInstance(context)
            val repository = ShoppingRepository(database.shoppingDao())
            val preferenceManager = PreferenceManager.getInstance(context)
            @Suppress("UNCHECKED_CAST")
            return ShoppingViewModel(repository, preferenceManager, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
