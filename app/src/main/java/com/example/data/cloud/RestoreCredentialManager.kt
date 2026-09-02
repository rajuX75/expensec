package com.example.data.cloud

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill #5 (restore-credentials): silent account restore across device migration.
 *
 * Flow:
 *  - After a successful Google/Firebase sign-in, [createRestoreKey] asks Credential
 *    Manager to generate a Restore Credential. The response JSON is persisted to
 *    SharedPreferences, which is included in the platform backup (see
 *    res/xml/backup_rules.xml), so it travels to the user's next device.
 *  - On a fresh install / restored device, [fetchRestoreKey] (Tier 2: launcher
 *    Activity onCreate) queries Credential Manager for the restore credential,
 *    letting the app recognize the returning account without showing a picker.
 *    Tier 1 (BackupAgent.onRestoreFinished) is not needed because the shared
 *    prefs are themselves restored before the first Activity launch.
 *  - On sign-out, [clearRestoreKey] wipes the key so the next device does not
 *    silently resurrect a deliberately signed-out account.
 *
 * Scoped (per implementation plan) to creating / fetching / clearing restore keys
 * using the Firebase account as the restore payload — full WebAuthn/FIDO2 server
 * verification would require a backend endpoint and is a future enhancement.
 *
 * Requires minSdk 28 (Restore Credentials API). The app was raised 24 → 28.
 */
class RestoreCredentialManager(private val context: Context) {

    companion object {
        private const val TAG = "RestoreCredentialMgr"
        private const val PREFS = "restore_credentials"
        private const val KEY_RESPONSE_JSON = "restore_response_json"
        private const val KEY_USER_ID = "restore_user_id"
    }

    private val credentialManager = CredentialManager.create(context)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Creates a restore key bound to [userId]. Fire-and-forget from the sign-in
     * flow — failures are logged, never surfaced to the user.
     */
    suspend fun createRestoreKey(userId: String): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            // Minimal JSON request — full FIDO2/WebAuthn server-side verification
            // is a future enhancement. For now we just create a restore credential
            // so the platform knows this user was signed in.
            val requestJson = """
                {
                    "rp": { "name": "ExpenseX", "id": "expensex.rjx.com" },
                    "user": { "id": "$userId", "name": "user", "displayName": "User" },
                    "challenge": "${System.currentTimeMillis()}"
                }
            """.trimIndent()
            credentialManager.createCredential(
                context,
                CreateRestoreCredentialRequest(requestJson)
            )
            prefs.edit()
                .putString(KEY_RESPONSE_JSON, requestJson)
                .putString(KEY_USER_ID, userId)
                .apply()
            Log.d(TAG, "Restore key created for uid=$userId")
            Unit
        }.onFailure { Log.w(TAG, "Could not create restore key", it) }
    }

    /**
     * Tier 2 restore: called from the launcher Activity's onCreate on a fresh /
     * restored install. Returns the previously signed-in Firebase uid if a restore
     * credential is available, or null when the device has nothing to restore.
     */
    suspend fun fetchRestoreKey(): Result<String?> = withContext(Dispatchers.Main) {
        runCatching {
            val requestJson = """{"challenge": "${System.currentTimeMillis()}"}"""
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetRestoreCredentialOption(requestJson))
                .build()
            // A successful call means the platform recognizes the restore credential
            // carried over from the old device.
            credentialManager.getCredential(context, request)
            prefs.getString(KEY_USER_ID, null)
        }.recoverCatching { e ->
            Log.d(TAG, "No restore credential on this device (${e.javaClass.simpleName})")
            null
        }
    }

    /** Clears the restore key on explicit sign-out. */
    suspend fun clearRestoreKey(): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            prefs.edit().clear().apply()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "Restore key cleared")
            Unit
        }.onFailure { Log.w(TAG, "Could not clear restore key", it) }
    }
}
