package com.portfolio.fincore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.portfolio.fincore.api.ApiModels.ApiErrorResponse;
import com.portfolio.fincore.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {
    private ApiExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MDC.clear();
        handler = new ApiExceptionHandler();
        request = mock(HttpServletRequest.class);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void mapsDomainValidationAndUnreadableRequestErrorsToSafeResponses() {
        ResponseEntity<ApiErrorResponse> domainError = handler.handleApiException(
                ApiException.badRequest("INVALID_MONTH", "Month must use YYYY-MM."),
                request);
        assertEquals(HttpStatus.BAD_REQUEST, domainError.getStatusCode());
        assertEquals("INVALID_MONTH", domainError.getBody().error().code());
        assertEquals("unknown", domainError.getBody().error().requestId());

        ResponseEntity<ApiErrorResponse> validationError = handler.handleValidationError(request);
        assertEquals("INVALID_INPUT", validationError.getBody().error().code());
        ResponseEntity<ApiErrorResponse> unreadableError = handler.handleUnreadableBody(request);
        assertEquals("INVALID_REQUEST", unreadableError.getBody().error().code());
        verify(request).setAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE, "INVALID_REQUEST");
    }

    @Test
    void hidesUnexpectedExceptionDetailsAndPropagatesTheRequestId() {
        MDC.put("requestId", "fincore-test-request");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedError(
                new IllegalStateException("private implementation detail"),
                request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().error().code());
        assertEquals("fincore-test-request", response.getBody().error().requestId());
        assertFalse(response.getBody().error().message().contains("private implementation detail"));
        verify(request).setAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE, "INTERNAL_ERROR");
    }
}
