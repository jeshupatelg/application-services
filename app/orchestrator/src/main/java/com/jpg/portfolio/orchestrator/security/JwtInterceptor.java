package com.jpg.portfolio.orchestrator.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Base64;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // Extract the Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid Authorization header");
            return false;
        }

        String token = authHeader.substring(7).trim();
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Malformed JWT token structure");
                return false;
            }

            // Decode the payload base64 part
            byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(decodedBytes);
            JsonNode payloadNode = objectMapper.readTree(payloadJson);

            // Fetch the username claim (Keycloak defaults to 'preferred_username' or 'sub')
            String username = null;
            if (payloadNode.has("preferred_username")) {
                username = payloadNode.get("preferred_username").asText();
            } else if (payloadNode.has("sub")) {
                username = payloadNode.get("sub").asText();
            }

            if (username == null || username.trim().isEmpty()) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Username claim not found in JWT payload");
                return false;
            }

            // Inject the extracted username into request context for the controller
            request.setAttribute("username", username);
            return true;

        } catch (Exception e) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Failed to parse JWT: " + e.getMessage());
            return false;
        }
    }
}
