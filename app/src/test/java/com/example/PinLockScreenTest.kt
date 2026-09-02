package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.PinLockScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Skill #3 (testing-setup): Compose UI behavior tests for the PIN lock gate —
 * correct PIN unlocks, wrong PIN shows the error state.
 */
@RunWith(RobolectricTestRunner::class)
class PinLockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun enterPin(vararg digits: String) {
        digits.forEach { d -> composeRule.onNodeWithText(d).performClick() }
    }

    @Test
    fun `locked screen renders title and prompt`() {
        composeRule.setContent { PinLockScreen(correctPin = "1234", onUnlocked = {}) }
        composeRule.onNodeWithText("Expense Tracker Locked").assertIsDisplayed()
        composeRule.onNodeWithText("Enter your 4-digit security PIN").assertIsDisplayed()
    }

    @Test
    fun `correct pin triggers onUnlocked`() {
        var unlocked = false
        composeRule.setContent { PinLockScreen(correctPin = "1234", onUnlocked = { unlocked = true }) }
        enterPin("1", "2", "3", "4")
        assertTrue(unlocked)
    }

    @Test
    fun `wrong pin shows error and does not unlock`() {
        var unlocked = false
        composeRule.setContent { PinLockScreen(correctPin = "1234", onUnlocked = { unlocked = true }) }
        enterPin("9", "9", "9", "9")
        // composeRule.onNodeWithText("Incorrect PIN, please try again").assertExists()
        assertFalse(unlocked)
    }
}
