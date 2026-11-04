package com.tianshu.backend.security;

public record CurrentUserPrincipal(String userId) {

    public CurrentUserPrincipal {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}
