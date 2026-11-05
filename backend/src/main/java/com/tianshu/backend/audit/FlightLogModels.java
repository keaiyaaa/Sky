package com.tianshu.backend.audit;

import java.time.Instant;
import java.util.List;

public final class FlightLogModels {
    private FlightLogModels() {}
    public record LogEntry(String id, String clientId, String category, String action, String result,
            String source, String detail, String taskId, Instant occurredAt, long version, String origin) {}
    public record LogPage(List<LogEntry> items, int page, int size, long total, boolean missionEventsAvailable) {}
    public record CreateLogRequest(String clientId, String category, String action, String result, String source,
            String detail, String taskId, String occurredAt, Long expectedVersion, String idempotencyKey) {}
    public record Query(int page, int size, String keyword, String source, String category, Instant from, Instant to) {}
    public record Stored(LogEntry entry, String requestHash) {}
}
