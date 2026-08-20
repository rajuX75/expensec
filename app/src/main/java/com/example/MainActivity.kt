package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.PinLockScreen
import com.example.ui.navigation.ExpenseAppMain
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExpenseViewModel

/**
 * Entry point for the app.
 * Responsibilities: Activity lifecycle, theme resolution, PIN lock gate.
 * Navigation and screen routing have been extracted to [com.example.ui.navigation.AppNavigation].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ExpenseViewModel = viewModel()

            // Observe settings
            val themeMode by viewModel.themeMode.collectAsState()
            val isPinLockEnabled by viewModel.isPinLockEnabled.collectAsState()
            val pinCode by viewModel.pinCode.collectAsState()
            val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()

            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                if (isPinLockEnabled && !isAppUnlocked) {
                    PinLockScreen(
                        correctPin = pinCode,
                        onVerifyPin = { entered -> viewModel.verifyPin(entered) },
                        onUnlocked = { viewModel.unlockApp() }
                    )
                } else {
                    ExpenseAppMain(viewModel = viewModel)
                }
            }
        }
    }
}
