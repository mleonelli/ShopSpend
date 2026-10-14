package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.ui.ShoppingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.PreferenceManager
import com.example.viewmodel.ShoppingViewModel
import com.example.viewmodel.ShoppingViewModelFactory
import com.google.android.gms.ads.MobileAds
import java.util.Locale

class MainActivity : ComponentActivity() {

  private val viewModel: ShoppingViewModel by viewModels {
    ShoppingViewModelFactory(applicationContext)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize the Google Mobile Ads SDK
    try {
      MobileAds.initialize(this) {}
    } catch (e: Exception) {
      // Graceful fallback in environments without Google Play services
    }

    setContent {
      val language by viewModel.language.collectAsState()
      val context = LocalContext.current

      val targetLocale = remember(language) {
        when (language) {
          PreferenceManager.LANG_IT -> Locale.ITALIAN
          PreferenceManager.LANG_EN -> Locale.ENGLISH
          else -> {
            val sysLang = Locale.getDefault().language.lowercase(Locale.ROOT)
            if (sysLang.startsWith("it")) Locale.ITALIAN else Locale.ENGLISH
          }
        }
      }

      LaunchedEffect(targetLocale) {
        Locale.setDefault(targetLocale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(targetLocale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
      }

      val localizedConfiguration = remember(targetLocale, context) {
        Configuration(context.resources.configuration).apply {
          setLocale(targetLocale)
        }
      }

      val localizedContext = remember(targetLocale, context) {
        val config = Configuration(context.resources.configuration).apply {
          setLocale(targetLocale)
        }
        context.createConfigurationContext(config)
      }

      CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext
      ) {
        MyApplicationTheme {
          Surface(modifier = Modifier.fillMaxSize()) {
            ShoppingScreen(viewModel = viewModel)
          }
        }
      }
    }
  }
}

