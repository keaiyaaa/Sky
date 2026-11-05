package com.example.skybackend.api;

import com.example.skybackend.ai.UpstreamServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponses.Error> invalidRequest(InvalidRequestException exception) {
        return ResponseEntity.badRequest().body(new ApiResponses.Error(exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponses.Error> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiResponses.Error("上传文件过大"));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiResponses.Error> upstreamFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiResponses.Error("云端模型暂时不可用，请稍后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponses.Error> unexpectedFailure() {
        return ResponseEntity.internalServerError()
                .body(new ApiResponses.Error("服务暂时不可用"));
    }
}
