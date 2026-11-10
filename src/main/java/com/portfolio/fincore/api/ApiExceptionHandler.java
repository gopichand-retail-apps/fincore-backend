package com.portfolio.fincore.api;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.portfolio.fincore.api.ApiModels.ApiErrorBody;
import com.portfolio.fincore.api.ApiModels.ApiErrorResponse;
import com.portfolio.fincore.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        LOGGER.atWarn()
                .addKeyValue("eventName", "api.request.rejected")
                .addKeyValue("errorCode", exception.getCode())
                .addKeyValue("outcome", "rejected")
                .log("Request rejected");
        return response(request, exception.getStatus(), exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationError(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Review the submitted fields.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request body is not valid.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Request failed", keyValue("eventName", "api.request.failed"),
            keyValue("errorCode", "INTERNAL_ERROR"),
            keyValue("exceptionType", exception.getClass().getSimpleName()),
            keyValue("outcome", "failure"));
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message) {
        request.setAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE, code);
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                new ApiErrorBody(code, message, Optional.ofNullable(MDC.get("requestId")).orElse("unknown"))));
    }
}