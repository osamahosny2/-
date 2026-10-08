package com.example

import android.content.Context
import android.test.mock.MockContext
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("إيروما منجا", appName)
  }

  @Test
  fun `verify launcher icon loads successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val iconDrawable = context.getDrawable(R.mipmap.ic_launcher)
    org.junit.Assert.assertNotNull("Launcher icon drawable should not be null", iconDrawable)
    val roundDrawable = context.getDrawable(R.mipmap.ic_launcher_round)
    org.junit.Assert.assertNotNull("Round launcher icon drawable should not be null", roundDrawable)
  }
}
