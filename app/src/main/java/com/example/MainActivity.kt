package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.ShoppingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ShoppingViewModel
import com.example.viewmodel.ShoppingViewModelFactory
import com.google.android.gms.ads.MobileAds

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
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          ShoppingScreen(viewModel = viewModel)
        }
      }
    }
  }
}

