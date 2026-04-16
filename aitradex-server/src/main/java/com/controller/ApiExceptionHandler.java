package com.controller;

import com.common.api.ApiResponse;
import com.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        HttpStatus status = e.getCode() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        log.error("IllegalStateException: {}", e.getMessage(), e);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String detail = e.getMessage() == null ? "bad_request" : e.getMessage();
        if (detail.contains("not_found")) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(status.value(), detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage() == null ? "internal_server_error" : e.getMessage()));
    }
}
