
// app/src/androidTest/java/com/imtiaz/ktimazrev/ExampleInstrumentedTest.kt
package com.imtiaz.ktimazrev

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Rule

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.imtiaz.ktimazrev.debug", appContext.packageName)
    }
    
    @Test
    fun app_launches_successfully() {
        // Test that the app launches and shows the main content
        composeTestRule.onNodeWithText("Tap the folder icon to open an ELF file.")
            .assertIsDisplayed()
    }
    
    @Test
    fun navigation_tabs_are_visible() {
        // Test that all navigation tabs are present
        composeTestRule.onNodeWithText("Disassembly").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hex View").assertIsDisplayed()
        composeTestRule.onNodeWithText("Symbols").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bookmarks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Graph").assertIsDisplayed()
    }
    
    @Test
    fun tab_navigation_works() {
        // Test tab switching
        composeTestRule.onNodeWithText("Symbols").performClick()
        composeTestRule.onNodeWithText("No symbols found or loaded. Please load an ELF file.")
            .assertIsDisplayed()
            
        composeTestRule.onNodeWithText("Bookmarks").performClick()
        composeTestRule.onNodeWithText("No bookmarks yet. Long press on an instruction to add one.")
            .assertIsDisplayed()
    }
}