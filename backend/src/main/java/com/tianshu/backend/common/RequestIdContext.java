package com.tianshu.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

public final class RequestIdContext {

    public static final String HEADER_NAME = "X-Request-Id";
    private static final String REQUEST_ATTRIBUTE = RequestIdContext.class.getName() + ".requestId";

    private RequestIdContext() {
    }

    public static String ensureRequestId(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        String requestId = existing instanceof String value && !value.isBlank()
                ? value
                : UUID.randomUUID().toString();

        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);
        return requestId;
    }

    public static String getRequestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        return UUID.randomUUID().toString();
    }
}
