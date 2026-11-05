package com.tianshu.backend.device;

import java.time.Instant;

public final class DeviceModels {
    private DeviceModels() {}
    public record DeviceResponse(String id, String clientId, String droneName, String model, String serialNo,
                                 String firmwareVersion, String source, long version, Instant createdAt,
                                 Instant updatedAt, boolean deleted) {}
    public record CreateDeviceRequest(String clientId, String droneName, String model, String serialNo,
                                      String firmwareVersion, String source, Long expectedVersion,
                                      String idempotencyKey) {}
    public record UpdateDeviceRequest(String droneName, String model, String firmwareVersion,
                                      Long expectedVersion, String idempotencyKey) {}
    public record DeleteDeviceRequest(Long expectedVersion, String idempotencyKey) {}
}
