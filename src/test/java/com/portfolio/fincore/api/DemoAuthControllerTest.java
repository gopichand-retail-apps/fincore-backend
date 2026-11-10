package com.portfolio.fincore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.portfolio.fincore.service.DemoAuthService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DemoAuthControllerTest {
    @Mock
    private DemoAuthService authService;

    @Mock
    private SecurityContextRepository securityContextRepository;

    private DemoAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new DemoAuthController(authService, securityContextRepository);
    }

    @Test
    void rejectsMissingAuthentication() {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> controller.currentUser(null));

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void rejectsAuthenticationWithoutADemoPrincipal() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "not-a-demo-principal", null, List.of());

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> controller.currentUser(authentication));

        assertEquals(401, exception.getStatusCode().value());
    }
}
