package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.ExpenseViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Skill #3 (testing-setup): ViewModel smoke tests via Robolectric — verifies
 * [ExpenseViewModel] constructs cleanly (all delegates wire up) and that the
 * PIN-unlock session state behaves correctly.
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseViewModelTest {

    @Test
    fun `viewModel constructs with all flows non-null`() {
        val vm = ExpenseViewModel(ApplicationProvider.getApplicationContext())
        assertNotNull(vm.themeMode)
        assertNotNull(vm.allTransactions)
        assertNotNull(vm.allCategories)
        assertNotNull(vm.firebaseUser)
        assertNotNull(vm.unreadNotificationCount)
    }

    @Test
    fun `app starts locked and unlockApp flips session state`() {
        val vm = ExpenseViewModel(ApplicationProvider.getApplicationContext())
        assertFalse(vm.isAppUnlocked.value)
        vm.unlockApp()
        assertTrue(vm.isAppUnlocked.value)
    }
}
