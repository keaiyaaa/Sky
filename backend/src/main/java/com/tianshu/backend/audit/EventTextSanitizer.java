package com.tianshu.backend.audit;

/** Business event text is never an unrestricted request/credential dump. */
public final class EventTextSanitizer {
    private EventTextSanitizer() {}
    public static String clean(String value) {
        return value.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [REDACTED]")
                .replaceAll("(?i)(password|passwd|accessToken|refreshToken|authorization|db_password|密码|令牌)[\"']?\\s*[=:：]\\s*(\"[^\"]*\"|'[^']*'|[^\\s,;]+)", "$1=[REDACTED]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[EMAIL]")
                .replaceAll("(?<![0-9])1[3-9][0-9]{9}(?![0-9])", "[PHONE]");
    }
}
