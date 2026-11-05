package com.tianshu.backend.common;

import org.springframework.http.HttpStatus;
import java.util.List;

/** Deliberately contains only messages safe to return to a client. */
public class ApiFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<ApiFieldError> fieldErrors;

    public ApiFailure(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiFailure(HttpStatus status, String code, String message, List<ApiFieldError> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fields);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public List<ApiFieldError> fieldErrors() { return fieldErrors; }

    public static ApiFailure invalid(String field, String reason) {
        return new ApiFailure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数无效",
                List.of(new ApiFieldError(field, reason)));
    }

    public static ApiFailure notFound() {
        return new ApiFailure(HttpStatus.NOT_FOUND, "NOT_FOUND", "记录不存在");
    }

    public static ApiFailure conflict() {
        return new ApiFailure(HttpStatus.CONFLICT, "VERSION_CONFLICT", "记录已变化，请刷新后重试");
    }
}
