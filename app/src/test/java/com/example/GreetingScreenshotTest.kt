package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.IrumaMangaTheme
import org.junit.Rule
import org.junit.Test

class GreetingScreenshotTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun greetingIsRendered() {
    composeTestRule.setContent {
      IrumaMangaTheme {
        Text("إيروما منجا")
      }
    }
    composeTestRule.onNodeWithText("إيروما منجا").assertExists()
  }
}
