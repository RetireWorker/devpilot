package com.devpilot.gateway.web;

import com.devpilot.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public GatewayAuthFilter(GatewayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/health") || path.startsWith("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }
        String configured = properties.getToken();
        if (configured == null || configured.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String supplied = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        if (!constantTimeEquals(configured, supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    Map.of("error", "unauthorized", "message", "invalid gateway token"));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
