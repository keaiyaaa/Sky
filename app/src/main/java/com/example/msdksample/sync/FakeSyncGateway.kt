package com.example.msdksample.sync

enum class SyncStatus { SYNCED, DUPLICATE, SESSION_EXPIRED, VERSION_CONFLICT }

data class SyncRecord(
    val id: String,
    val version: Long,
    val idempotencyKey: String,
    val sessionGeneration: Long,
    val payload: String
)

/** In-memory demonstration only; it does not make network requests. */
class FakeSyncGateway {
    private val records = mutableMapOf<String, SyncRecord>()
    private val acceptedKeys = mutableMapOf<String, Long>()

    fun sync(record: SyncRecord, activeSessionGeneration: Long): SyncStatus {
        if (record.sessionGeneration != activeSessionGeneration) return SyncStatus.SESSION_EXPIRED
        if (acceptedKeys[record.idempotencyKey] == record.sessionGeneration) return SyncStatus.DUPLICATE
        val current = records[record.id]
        if (current != null && record.version <= current.version) return SyncStatus.VERSION_CONFLICT
        records[record.id] = record
        acceptedKeys[record.idempotencyKey] = record.sessionGeneration
        return SyncStatus.SYNCED
    }

    fun find(id: String): SyncRecord? = records[id]

    fun clearSession(sessionGeneration: Long) {
        acceptedKeys.entries.removeAll { it.value == sessionGeneration }
        records.entries.removeAll { it.value.sessionGeneration == sessionGeneration }
    }
}
