package com.tianshu.backend.common;

import java.util.List;

public record ApiError(
        String code,
        String message,
        String requestId,
        List<ApiFieldError> fieldErrors
) {
    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
