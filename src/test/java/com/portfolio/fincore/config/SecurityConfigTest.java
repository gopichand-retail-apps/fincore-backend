package com.portfolio.fincore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityConfigTest {
    @Test
    void writesSafeFallbackRequestIdWhenNoRequestIdHeaderExists() throws Exception {
        SecurityConfig configuration = new SecurityConfig(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReflectionTestUtils.invokeMethod(
                configuration,
                "writeError",
                request,
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "AUTH_REQUIRED",
                "Sign in to continue.");

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("AUTH_REQUIRED", request.getAttribute(RequestIdFilter.ERROR_CODE_ATTRIBUTE));
        assertTrue(response.getContentAsString().contains("\"requestId\":\"unknown\""));
    }
}
