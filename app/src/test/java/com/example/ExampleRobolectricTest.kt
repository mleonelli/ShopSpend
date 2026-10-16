package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.VoiceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ShopSpend", appName)
  }

  @Test
  fun `test voice parser with item and price`() {
    val parsed = VoiceParser.parse("Milk 2.50")
    assertNotNull(parsed)
    assertEquals("Milk", parsed?.name)
    assertEquals(2.50, parsed?.price ?: 0.0, 0.001)
    assertEquals(1, parsed?.quantity)
  }

  @Test
  fun `test voice parser with quantity item and price`() {
    val parsed = VoiceParser.parse("3 Apples for $4.99")
    assertNotNull(parsed)
    assertEquals("Apples", parsed?.name)
    assertEquals(4.99, parsed?.price ?: 0.0, 0.001)
    assertEquals(3, parsed?.quantity)
  }
  @Test
  fun `test activity launch`() {
    androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
      }
    }
  }
}

