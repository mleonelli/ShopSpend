package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shopspend_prefs", Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _currency = MutableStateFlow(prefs.getString(KEY_CURRENCY, "$") ?: "$")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _isVibrationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION_ENABLED, true))
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    fun setPremium(premium: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, premium).apply()
        _isPremium.value = premium
    }

    fun setCurrency(symbol: String) {
        prefs.edit().putString(KEY_CURRENCY, symbol).apply()
        _currency.value = symbol
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        _language.value = lang
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        _isVibrationEnabled.value = enabled
    }

    /**
     * Resolves the active language: returns "it" or "en".
     * If user chose "system", checks the device system default locale.
     */
    fun getEffectiveLanguage(): String {
        return when (val chosen = _language.value) {
            LANG_IT -> LANG_IT
            LANG_EN -> LANG_EN
            else -> {
                val sysLang = Locale.getDefault().language.lowercase(Locale.ROOT)
                if (sysLang.startsWith("it")) LANG_IT else LANG_EN
            }
        }
    }

    /**
     * Returns the effective Locale for formatting, speech recognition, and UI.
     */
    fun getEffectiveLocale(): Locale {
        return when (getEffectiveLanguage()) {
            LANG_IT -> Locale("it", "IT")
            else -> Locale("en", "US")
        }
    }

    companion object {
        private const val KEY_IS_PREMIUM = "is_premium_purchased"
        private const val KEY_CURRENCY = "preferred_currency"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_VIBRATION_ENABLED = "vibration_alerts_enabled"

        const val LANG_SYSTEM = "system"
        const val LANG_EN = "en"
        const val LANG_IT = "it"

        @Volatile
        private var INSTANCE: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferenceManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
