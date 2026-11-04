package com.tianshu.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toApiFieldError)
                .toList();
        return error(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_FAILED,
                "请求参数无效", fieldErrors, request, response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        List<ApiFieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_FAILED,
                "请求参数无效", fieldErrors, request, response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_FAILED,
                "请求 JSON 无效", List.of(), request, response);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return error(HttpStatus.NOT_FOUND, ApiErrorCodes.NOT_FOUND,
                "请求的资源不存在", List.of(), request, response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCodes.METHOD_NOT_ALLOWED,
                "请求方法不受支持", List.of(), request, response);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleMissingAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return error(HttpStatus.UNAUTHORIZED, ApiErrorCodes.UNAUTHENTICATED,
                "需要登录", List.of(), request, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return error(HttpStatus.FORBIDDEN, ApiErrorCodes.FORBIDDEN,
                "无权执行此操作", List.of(), request, response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String requestId = RequestIdContext.ensureRequestId(request, response);
        LOGGER.error("Unhandled API failure requestId={} exceptionType={}",
                requestId, exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(RequestIdContext.HEADER_NAME, requestId)
                .body(new ApiError(ApiErrorCodes.INTERNAL_ERROR,
                        "服务暂时无法处理请求", requestId, List.of()));
    }

    private ApiFieldError toApiFieldError(FieldError fieldError) {
        String reason = fieldError.getDefaultMessage() == null
                ? "字段值无效"
                : fieldError.getDefaultMessage();
        return new ApiFieldError(fieldError.getField(), reason);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            List<ApiFieldError> fieldErrors,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String requestId = RequestIdContext.ensureRequestId(request, response);
        return ResponseEntity.status(status)
                .header(RequestIdContext.HEADER_NAME, requestId)
                .body(new ApiError(code, message, requestId, fieldErrors));
    }
}
