package com.example.msdksample.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeSyncGatewayTest {
    private fun record(version: Long, key: String, generation: Long = 1) =
        SyncRecord("task-1", version, key, generation, "demo-payload")

    @Test fun rejectsExpiredSessionAndDeduplicatesAcceptedKeys() {
        val gateway = FakeSyncGateway()
        assertEquals(SyncStatus.SESSION_EXPIRED, gateway.sync(record(1, "old", 0), 1))
        assertEquals(SyncStatus.SYNCED, gateway.sync(record(1, "key-1"), 1))
        assertEquals(SyncStatus.DUPLICATE, gateway.sync(record(2, "key-1"), 1))
        assertEquals(1L, gateway.find("task-1")?.version)
    }

    @Test fun versionConflictDoesNotOverwriteCurrentRecord() {
        val gateway = FakeSyncGateway()
        gateway.sync(record(2, "key-2"), 1)
        assertEquals(SyncStatus.VERSION_CONFLICT, gateway.sync(record(1, "key-1"), 1))
        assertEquals(2L, gateway.find("task-1")?.version)
    }

    @Test fun clearingSessionRemovesItsDemoData() {
        val gateway = FakeSyncGateway()
        gateway.sync(record(1, "key-1"), 1)
        gateway.clearSession(1)
        assertNull(gateway.find("task-1"))
        assertEquals(SyncStatus.SYNCED, gateway.sync(record(1, "key-1", 2), 2))
    }
}
