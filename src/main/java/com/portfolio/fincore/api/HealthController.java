package com.portfolio.fincore.api;

import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final BuildProperties buildProperties;

    public HealthController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "fincore-api");
    }

    @GetMapping("/api/v1/version")
    public Map<String, String> version(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("service", "fincore-api", "version", buildProperties.getVersion());
    }
}