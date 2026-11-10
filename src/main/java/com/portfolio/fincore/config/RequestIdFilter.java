package com.portfolio.fincore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ERROR_CODE_ATTRIBUTE = "fincore.errorCode";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,80}");
    private final BuildProperties buildProperties;

    public RequestIdFilter(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveCorrelationId(request.getHeader("X-Request-ID"));
        String traceId = resolveCorrelationId(request.getHeader("X-Trace-ID"));
        long startNanos = System.nanoTime();
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        response.setHeader("X-Request-ID", requestId);
        response.setHeader("X-Trace-ID", traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            logCompletion(request, response, startNanos);
            MDC.remove("requestId");
            MDC.remove("traceId");
        }
    }

    private String resolveCorrelationId(String provided) {
        if (provided != null && SAFE_CORRELATION_ID.matcher(provided).matches()) {
            return provided;
        }
        return UUID.randomUUID().toString();
    }

    private void logCompletion(HttpServletRequest request, HttpServletResponse response, long startNanos) {
        Object mappedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String routeTemplate = mappedPattern instanceof String value ? value : "/unmatched";
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        int status = response.getStatus();
        var logger = status >= 500 ? LOGGER.atError() : status >= 400 ? LOGGER.atWarn() : LOGGER.atInfo();
        Object errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        if (errorCode instanceof String code) {
            logger.addKeyValue("errorCode", code);
        }
        logger.addKeyValue("service", "fincore-api")
            .addKeyValue("version", buildProperties.getVersion())
                .addKeyValue("eventName", status >= 400 ? "api.request.failed" : "api.request.completed")
                .addKeyValue("routeTemplate", routeTemplate)
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("httpStatus", status)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("outcome", status >= 400 ? "failure" : "success")
                .log("Request completed");
    }
}