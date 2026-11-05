package com.tianshu.backend.profile;

import java.time.Instant;

public final class ProfileModels {
    private ProfileModels() {}
    public record ProfileResponse(String userId, String username, String nickname, String email,
                                  String organization, long version, Instant updatedAt) {}
    // All editable fields are submitted together; empty strings clear optional fields.
    public record UpdateProfileRequest(String nickname, String email, String organization, Long expectedVersion) {}
}
