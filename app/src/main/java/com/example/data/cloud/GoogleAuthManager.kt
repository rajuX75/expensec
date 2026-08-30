package com.example.data.cloud

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.repository.UserPreferencesRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Google authentication for two different purposes:
 *
 *  1. Firebase Auth    -> needs a Google **ID token**  (an OIDC JWT).
 *  2. Google Drive API -> needs a Google **access token** for the `drive.appdata` scope.
 *
 * These are DIFFERENT tokens. The original implementation stored the Firebase ID token and then
 * sent that same ID token to the Drive REST client as a bearer token, which always fails with
 * HTTP 401 "Invalid Credentials" against the Drive API.
 *
 * Fixed flow:
 *  - `signIn(...)`          : Credential Manager "Sign in with Google" -> ID token -> Firebase Auth.
 *  - `authorizeDrive(...)`  : Play Services `AuthorizationClient` with the `drive.appdata` scope ->
 *                             a real Drive access token, stored separately for the Drive client.
 *
 * The "No credentials available" (`NoCredentialException`) failure is handled by retrying the
 * request with `.setFilterByAuthorizedAccounts(false)`, which brings up the full account picker.
 * When even that is impossible (no Google account / no Play Services), a clear, human-readable
 * error is returned instead of the raw empty message.
 */
class GoogleAuthManager(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "GoogleAuthManager"

        // Matches the "Web client (auto-created for Google Sign-in)" OAuth client in
        // google-services.json. This is the client ID Credential Manager / Firebase expect.
        const val DEFAULT_WEB_CLIENT_ID =
            "871963300184-bh56vi8uai8us6pkscf6gehd0h7bkg6a.apps.googleusercontent.com"

        // Hidden per-app folder — the same location DrivesClient reads/writes.
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }

    private val credentialManager = CredentialManager.create(context)

    // BUG FIX #7: FirebaseAuth.getInstance() throws when Firebase is not initialised
    // (missing/invalid google-services.json, or right after an app update). Keep the
    // reference nullable + lazy so constructing this manager can never crash the app,
    // and every consumer degrades to a friendly error instead of an exception.
    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth is not available on this device", e)
            null
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        try {
            _currentUser.value = firebaseAuth?.currentUser
            firebaseAuth?.addAuthStateListener { auth ->
                _currentUser.value = auth.currentUser
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Firebase auth state listener", e)
        }
    }

    val currentUserId: String?
        get() = try {
            firebaseAuth?.currentUser?.uid
        } catch (e: Exception) {
            null
        }

    val isAuthenticated: Boolean
        get() = try {
            firebaseAuth?.currentUser != null
        } catch (e: Exception) {
            false
        }

    /**
     * Signs in with Google (ID token) into Firebase Auth.
     * Converts all exceptions to human-readable messages for the UI.
     *
     * @param activityContext must be an Activity so the interactive account picker can be shown.
     */
    suspend fun signIn(activityContext: Context, webClientId: String = ""): Result<String> =
        withContext(Dispatchers.Main) {
            val serverClientId = webClientId.ifBlank { DEFAULT_WEB_CLIENT_ID }
            runCatching {
                signInWithFirebase(activityContext, serverClientId)
            }.recoverCatching { throwable ->
                Log.e(TAG, "Google sign-in failed", throwable)
                throw throwable.toReadableSignInError()
            }
        }

    /**
     * Same as [signIn] but does NOT convert exceptions — the raw exception type is
     * preserved so callers (e.g. [CloudDelegate]) can inspect it and decide whether
     * to fall back to the legacy [GoogleSignInClient] path (Xiaomi/MIUI fix).
     */
    suspend fun signInInternal(activityContext: Context, webClientId: String = ""): Result<String> =
        withContext(Dispatchers.Main) {
            val serverClientId = webClientId.ifBlank { DEFAULT_WEB_CLIENT_ID }
            runCatching {
                signInWithFirebase(activityContext, serverClientId)
            }
        }

    // ── Legacy GoogleSignIn (Xiaomi / MIUI fallback) ─────────────────────────

    /**
     * Builds a classic [GoogleSignInClient] sign-in [Intent].
     *
     * Use this as a fallback when the modern Credential Manager API fails
     * (e.g. on Xiaomi MIUI where the Credential Manager bottom-sheet is blocked).
     * Launch the returned intent via [ActivityResultLauncher] and process the
     * result with [handleLegacySignInResult].
     */
    fun getLegacySignInIntent(context: Context): android.content.Intent {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(DEFAULT_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    /**
     * Processes the [android.content.Intent] returned by the legacy GoogleSignIn flow,
     * extracts the ID token, and completes Firebase sign-in.
     */
    suspend fun handleLegacySignInResult(data: android.content.Intent?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = firebaseAuth
                    ?: throw Exception("Firebase is not initialised on this device.")
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn
                    .getSignedInAccountFromIntent(data)
                val account = task.getResult(
                    com.google.android.gms.common.api.ApiException::class.java
                )
                val idToken = account.idToken
                    ?: throw Exception("Google Sign-In did not return an ID token.")
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                _currentUser.value = authResult.user
                Log.d(TAG, "Legacy Firebase sign-in successful: ${authResult.user?.uid}")
                val email = authResult.user?.email ?: account.email ?: ""
                userPreferencesRepository.setGoogleAccount(email.ifBlank { null }, null)
                email
            }.recoverCatching { t ->
                Log.e(TAG, "Legacy sign-in result handling failed", t)
                throw t.toReadableSignInError()
            }
        }

    private suspend fun signInWithFirebase(activityContext: Context, serverClientId: String): String {
        // BUG FIX #7: surfaced as a readable Result.failure, never a raw crash.
        val auth = firebaseAuth
            ?: throw Exception("Firebase is not initialised on this device. Please update or reinstall the app.")
        var lastFailure: Throwable? = null

        // Pass 1: use an already-authorized account (silent). Pass 2: full account picker.
        for (filterAuthorized in listOf(true, false)) {
            try {
                val idToken = requestGoogleIdToken(activityContext, serverClientId, filterAuthorized)
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                _currentUser.value = authResult.user
                Log.d(TAG, "Firebase sign-in successful: ${authResult.user?.uid}")

                val email = authResult.user?.email
                    ?: decodeEmailFromIdToken(idToken)
                    ?: ""
                userPreferencesRepository.setGoogleAccount(email.ifBlank { null }, null)
                return email
            } catch (t: Throwable) {
                lastFailure = t
                // Only worthwhile to retry with the full picker for the "no saved account" case.
                val isNoCredential = t is NoCredentialException ||
                    generateSequence(t) { it.cause }.any { it is NoCredentialException }
                if (!isNoCredential) break
            }
        }

        throw lastFailure ?: Exception("Google Sign-In failed: no Google account was available.")
    }

    private suspend fun requestGoogleIdToken(
        activityContext: Context,
        serverClientId: String,
        filterAuthorized: Boolean
    ): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterAuthorized)
            .setAutoSelectEnabled(filterAuthorized)
            .setServerClientId(serverClientId)
            .build()

        val requestBuilder = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)

        // On the full-picker pass, also include GetSignInWithGoogleOption.
        // This shows the standard Google bottom-sheet account selector and works on
        // release builds even when the release-keystore SHA-1 has not been registered
        // in Firebase / Google Cloud Console, which is the most common cause of
        // NoCredentialException on fresh installs.
        if (!filterAuthorized) {
            val signInWithGoogleOption = GetSignInWithGoogleOption
                .Builder(serverClientId)
                .build()
            requestBuilder.addCredentialOption(signInWithGoogleOption)
        }

        val result: GetCredentialResponse = credentialManager.getCredential(
            context = activityContext,
            request = requestBuilder.build()
        )

        // Both GetGoogleIdOption and GetSignInWithGoogleOption return a
        // GoogleIdTokenCredential, so createFrom() works for either.
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val idToken = credential.idToken
        if (idToken.isNullOrBlank()) {
            throw Exception("Google returned an empty ID token.")
        }
        return idToken
    }

    /**
     * Requests a Google Drive access token via the Play Services `AuthorizationClient`.
     * On first use, Google returns a consent `PendingIntent` that the UI must launch; after the
     * user grants consent, calling this again returns the token without a resolution.
     */
    suspend fun authorizeDrive(activityContext: Context): DriveAuthorizeResult =
        withContext(Dispatchers.Main) {
            try {
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                    .build()

                val result: AuthorizationResult =
                    Identity.getAuthorizationClient(activityContext).authorize(request).await()

                when {
                    result.hasResolution() -> {
                        val sender = result.pendingIntent?.intentSender
                        if (sender == null) {
                            DriveAuthorizeResult.Failed("Google Drive consent could not be started.")
                        } else {
                            DriveAuthorizeResult.ConsentRequired(sender)
                        }
                    }

                    !result.accessToken.isNullOrBlank() -> {
                        userPreferencesRepository.setDriveAccessToken(result.accessToken)
                        DriveAuthorizeResult.Granted(result.accessToken!!)
                    }

                    else -> DriveAuthorizeResult.Failed("Google Drive did not return an access token.")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Drive authorization failed", t)
                DriveAuthorizeResult.Failed(
                    "Google Drive authorization failed: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            try {
                firebaseAuth?.signOut()
                _currentUser.value = null
            } catch (_: Exception) {
            }
            userPreferencesRepository.setGoogleAccount(null, null)
            userPreferencesRepository.setDriveAccessToken(null)
        }
    }
}

/** Outcome of a Google Drive authorization attempt. */
sealed interface DriveAuthorizeResult {
    /** User must grant consent; launch [intentSender] with an Activity result launcher. */
    data class ConsentRequired(val intentSender: android.content.IntentSender) : DriveAuthorizeResult

    /** Token acquired (and already persisted). */
    data class Granted(val accessToken: String) : DriveAuthorizeResult

    /** Authorization could not proceed. */
    data class Failed(val message: String) : DriveAuthorizeResult
}

private fun decodeEmailFromIdToken(idToken: String): String? {
    return runCatching {
        val parts = idToken.split(".")
        if (parts.size < 2) return@runCatching null
        val payload = parts[1].replace('-', '+').replace('_', '/')
        val padded = payload.padEnd((payload.length + 3) / 4 * 4, '=')
        val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE)
        org.json.JSONObject(String(decoded, Charsets.UTF_8)).optString("email").takeIf { it.isNotBlank() }
    }.getOrNull()
}

internal fun Throwable.toReadableSignInError(): Exception {
    val chain = generateSequence(this) { it.cause }.toList()

    if (this is NoCredentialException || chain.any { it is NoCredentialException }) {
        // Check if running on Xiaomi / MIUI where Credential Manager is commonly blocked
        val isMiui = !android.os.Build.MANUFACTURER.isNullOrBlank() &&
            android.os.Build.MANUFACTURER.lowercase().contains("xiaomi")
        return if (isMiui) {
            Exception(
                "Google Sign-In could not open on this Xiaomi device. " +
                    "Please go to Settings → Apps → Expense Tracker → Battery → " +
                    "set to 'No restrictions', then try again."
            )
        } else {
            Exception(
                "Google Sign-In could not show the account picker. " +
                    "Please ensure Google Play Services is up to date, then try again. " +
                    "(If this is a sideloaded APK, the app's signing certificate may need to be " +
                    "registered in the Firebase Console.)"
            )
        }
    }

    if (this is GetCredentialCancellationException) {
        return Exception("Google Sign-In was cancelled.")
    }
    if (this is GetCredentialInterruptedException) {
        return Exception("Google Sign-In was interrupted. Please try again.")
    }
    if (this is GetCredentialProviderConfigurationException) {
        return Exception(
            "Google Sign-In is not configured on this device. " +
                "Please update Google Play Services and ensure the app has not been " +
                "restricted in MIUI battery or security settings."
        )
    }
    if (this is GetCredentialCustomException) {
        return Exception(
            "Google Sign-In failed: " +
                (this.message?.takeIf { it.isNotBlank() } ?: "unknown provider error.")
        )
    }
    if (this is GetCredentialUnknownException) {
        return Exception("Google Sign-In failed unexpectedly. Please try again or update Google Play Services.")
    }
    if (this is GetCredentialException) {
        return Exception(
            "Google Sign-In failed or is unavailable on this device. " +
                "If you are on a Xiaomi / MIUI device, try disabling battery optimization for this app."
        )
    }

    if (this is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
        return Exception("Google Sign-In failed: the credential is invalid or has expired.")
    }
    if (this is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
        return Exception("This Google account is already linked to a different sign-in method.")
    }
    if (this is com.google.firebase.auth.FirebaseAuthException) {
        return Exception("Google Sign-In failed: ${this.message ?: "Firebase rejected the sign-in."}")
    }

    return Exception(this.message?.takeIf { it.isNotBlank() } ?: "Google Sign-In failed.")
}

