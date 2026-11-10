package com.portfolio.fincore.api;

import com.portfolio.fincore.api.ApiModels.AuthResponse;
import com.portfolio.fincore.api.ApiModels.CsrfResponse;
import com.portfolio.fincore.api.ApiModels.DemoLoginRequest;
import com.portfolio.fincore.api.ApiModels.DemoProfilesResponse;
import com.portfolio.fincore.api.ApiModels.UserResponse;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.service.DemoAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@Profile({"local", "test"})
public class DemoAuthController {
    private final DemoAuthService authService;
    private final SecurityContextRepository securityContextRepository;

    public DemoAuthController(
            DemoAuthService authService,
            SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrfToken(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @GetMapping("/demo-profiles")
    public DemoProfilesResponse demoProfiles() {
        return new DemoProfilesResponse(authService.availableDemoProfiles());
    }

    @PostMapping("/demo-login")
    public AuthResponse demoLogin(
            @Valid @RequestBody DemoLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        DemoUser user = authService.findDemoUser(request.demoAccount());
        DemoPrincipal principal = authService.principal(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return new AuthResponse(authService.toResponse(principal));
    }

    @GetMapping("/me")
    public AuthResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof DemoPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        UserResponse user = authService.toResponse(principal);
        return new AuthResponse(user);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}