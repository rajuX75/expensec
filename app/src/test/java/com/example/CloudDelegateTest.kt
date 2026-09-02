package com.example

import android.app.Application
import com.example.data.cloud.CloudBackupRepository
import com.example.data.cloud.FirestoreSyncManager
import com.example.data.cloud.GoogleAuthManager
import com.example.data.repository.ImportExportRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.viewmodel.CloudDelegate
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skill #3 (testing-setup): MockK-based tests for the Google sign-in delegation
 * path in [CloudDelegate] — success and failure propagation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudDelegateTest {

    private fun newDelegate(
        auth: GoogleAuthManager,
        scope: kotlinx.coroutines.CoroutineScope
    ) = CloudDelegate(
        application = mockk<Application>(relaxed = true),
        viewModelScope = scope,
        importExportRepo = mockk<ImportExportRepository>(relaxed = true),
        cloudBackupRepo = mockk<CloudBackupRepository>(relaxed = true),
        googleAuthManager = auth,
        firestoreSyncManager = mockk<FirestoreSyncManager>(relaxed = true),
        userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    )

    @Test
    fun `signInGoogle propagates success email to onResult`() = runTest {
        val auth = mockk<GoogleAuthManager>()
        coEvery { auth.signInInternal(any(), any()) } returns Result.success("user@example.com")

        var captured: Result<String>? = null
        newDelegate(auth, this).signInGoogle(mockk(relaxed = true)) { captured = it }
        advanceUntilIdle()

        assertTrue(captured!!.isSuccess)
        assertEquals("user@example.com", captured!!.getOrNull())
    }

    @Test
    fun `signInGoogle converts failure to readable error without legacy fallback`() = runTest {
        val auth = mockk<GoogleAuthManager>()
        coEvery { auth.signInInternal(any(), any()) } returns Result.failure(Exception("boom"))

        var captured: Result<String>? = null
        // No fallback handler: failure must be delivered to onResult.
        newDelegate(auth, this).signInGoogle(mockk(relaxed = true), onFallbackToLegacy = null) { captured = it }
        advanceUntilIdle()

        assertTrue(captured!!.isFailure)
    }
}
