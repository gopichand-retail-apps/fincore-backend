package com.portfolio.fincore.service;

import com.portfolio.fincore.api.ApiException;
import com.portfolio.fincore.api.ApiModels.DemoProfileResponse;
import com.portfolio.fincore.api.ApiModels.UserResponse;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.repository.DemoUserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoAuthService {
    private final DemoUserRepository users;

    public DemoAuthService(DemoUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public DemoUser findDemoUser(String demoAccount) {
        return users.findByDemoKey(demoAccount)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "DEMO_ACCOUNT_NOT_FOUND", "That demo profile is not available."));
    }

    @Transactional(readOnly = true)
    public List<DemoProfileResponse> availableDemoProfiles() {
        return users.findAll(Sort.by("demoKey")).stream()
                .map(user -> new DemoProfileResponse(user.getDemoKey(), user.getRole()))
                .toList();
    }

    public DemoPrincipal principal(DemoUser user) {
        return new DemoPrincipal(user.getId(), user.getDisplayName(), user.getRole());
    }

    public UserResponse toResponse(DemoPrincipal principal) {
        return new UserResponse(principal.userId(), principal.displayName(), principal.role());
    }
}