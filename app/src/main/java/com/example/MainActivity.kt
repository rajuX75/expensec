package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.cloud.RestoreCredentialManager
import com.example.ui.components.PinLockScreen
import com.example.ui.navigation.ExpenseAppMain
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.utils.CrashLogCapture
import kotlinx.coroutines.launch


/**
 * Entry point for the app.
 * Responsibilities: Activity lifecycle, theme resolution, PIN lock gate.
 * Navigation and screen routing have been extracted to [com.example.ui.navigation.AppNavigation].
 */
class MainActivity : ComponentActivity() {

    // Skill #5 (restore-credentials): Tier 2 silent-restore check on launch.
    private val restoreCredentialManager by lazy { RestoreCredentialManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install crash capture before anything else so all subsequent uncaught
        // exceptions are written to disk and available for the next feedback report.
        CrashLogCapture.install(this)
        super.onCreate(savedInstanceState)
        handleAppUpgrade()
        attemptSilentRestore()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
                    ExpenseAppMain(viewModel = viewModel, deepLink = intent?.data)
                }
            }
        }
    }

    /**
     * Skill #5, Tier 2 restore fallback: on a fresh install or a device restored
     * from backup, ask Credential Manager for the restore key before any sign-in
     * UI is shown. A returning user is recognized silently; a genuinely new
     * device simply has no credential and this no-ops.
     */
    private fun attemptSilentRestore() {
        lifecycleScope.launch {
            val restoredUid = restoreCredentialManager.fetchRestoreKey().getOrNull()
            if (restoredUid != null) {
                android.util.Log.i(
                    "MainActivity",
                    "Restore credential recognized returning account: $restoredUid"
                )
            }
        }
    }

    /**
     * BUG FIX #8: Detects an app version upgrade and performs safe housekeeping so
     * stale state left behind by a previous version cannot crash the first launch:
     *  - cancels the uniquely-named WorkManager jobs enqueued by the older version
     *    (their serialized state may be incompatible with the new worker code),
     *  - records the new versionCode.
     * User data (Room database, preferences) is fully preserved — users no longer
     * need to "clear data" after an update.
     */
    private fun handleAppUpgrade() {
        try {
            val prefs = getSharedPreferences("app_internal_state", MODE_PRIVATE)
            val currentVersionCode = try {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            } catch (e: Exception) {
                -1
            }
            if (currentVersionCode == -1) return

            val lastVersionCode = prefs.getInt("last_run_version_code", -1)
            if (lastVersionCode == -1) {
                // First run (fresh install, or first launch after this fix shipped).
                prefs.edit().putInt("last_run_version_code", currentVersionCode).apply()
                return
            }

            if (currentVersionCode != lastVersionCode) {
                android.util.Log.i(
                    "MainActivity",
                    "App version changed: $lastVersionCode -> $currentVersionCode. Running upgrade housekeeping."
                )
                try {
                    androidx.work.WorkManager.getInstance(this)
                        .cancelUniqueWork("CloudinaryImageUpload")
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Could not cancel stale work: ${e.message}")
                }
                prefs.edit().putInt("last_run_version_code", currentVersionCode).apply()
            }
        } catch (e: Exception) {
            // Upgrade housekeeping must never block or crash app startup.
            android.util.Log.w("MainActivity", "Upgrade check failed", e)
        }
    }
}
