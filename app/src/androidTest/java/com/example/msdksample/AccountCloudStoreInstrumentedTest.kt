package com.example.msdksample

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.data.remote.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountCloudStoreInstrumentedTest {
    @Test fun exactPendingWriteSurvivesReopenAndCannotCrossAccounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "account-cloud-${UUID.randomUUID()}.db"
        var store = AccountCloudStore(context, name)
        try {
            val pending = store.pending("alice", "create:SN-A", "POST", "devices") { "original-client-id-and-key" }
            store.saveSnapshot("alice", "profile", "alice-profile")
            store.saveSnapshot("bob", "profile", "bob-profile")
            store.close(); store = AccountCloudStore(context, name)
            assertEquals(pending, store.pending("alice", "create:SN-A", "POST", "devices") { error("Must reuse stored request") })
            assertTrue(store.pending("bob").isEmpty())
            assertEquals("alice-profile", store.snapshot("alice", "profile"))
            assertEquals("bob-profile", store.snapshot("bob", "profile"))
            store.confirm(pending.copy(userId = "bob"))
            assertEquals(listOf(pending), store.pending("alice"))
            store.confirm(pending)
            assertTrue(store.pending("alice").isEmpty())
        } finally { store.close(); context.deleteDatabase(name) }
    }

    @Test fun conflictingPendingOperationsDoNotReplaceOriginalIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "account-cloud-${UUID.randomUUID()}.db"
        val store = AccountCloudStore(context, name)
        try {
            val original = store.pending("alice", "device:42", "PUT", "devices/42") { "rename-draft" }
            try {
                store.pending("alice", "device:42", "DELETE", "devices/42") { "delete" }
                fail("An unconfirmed write must be resolved before a different action")
            } catch (_: IllegalStateException) { }
            assertEquals(listOf(original), store.pending("alice"))
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
