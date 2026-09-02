package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.PinLockScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Skill #3 (testing-setup): Roborazzi screenshot baselines, expanded beyond the
 * original single greeting test. Run `./gradlew recordRoborazziDebug` to
 * (re)generate baselines and `./gradlew verifyRoborazziDebug` in CI.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pinLockScreen_lightTheme() {
        composeRule.setContent {
            MaterialTheme {
                PinLockScreen(correctPin = "1234", onUnlocked = {})
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun pinLockScreen_darkTheme() {
        composeRule.setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                PinLockScreen(correctPin = "1234", onUnlocked = {})
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
