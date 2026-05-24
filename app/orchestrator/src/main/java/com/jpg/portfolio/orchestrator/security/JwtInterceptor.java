package com.jpg.portfolio.orchestrator.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // Extract the Authorization header or Cookie (for local testing support)
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else {
            // Check cookies for local testing convenience
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if ("auth_token".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }

        if (token == null || token.trim().isEmpty()) {
            sendUnauthorizedResponse(request, response, "Missing or invalid Authorization credentials");
            return false;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                sendUnauthorizedResponse(request, response, "Malformed JWT token structure");
                return false;
            }

            // Decode the payload base64 part
            byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(decodedBytes);
            JsonNode payloadNode = objectMapper.readTree(payloadJson);

            // Strictly extract preferred_username and name claims per OIDC contract
            String preferredUsername = null;
            if (payloadNode.has("preferred_username")) {
                preferredUsername = payloadNode.get("preferred_username").asText();
            }

            String name = null;
            if (payloadNode.has("name")) {
                name = payloadNode.get("name").asText();
            }

            if (preferredUsername == null || preferredUsername.trim().isEmpty()) {
                sendUnauthorizedResponse(request, response, "preferred_username claim not found in JWT payload");
                return false;
            }

            // Fallback: Use preferredUsername as alias if name is null/empty
            String alias = (name == null || name.trim().isEmpty()) ? preferredUsername : name;

            // Inject preferredUsername as "username" and alias as "alias" for downstream context
            request.setAttribute("username", preferredUsername);
            request.setAttribute("alias", alias);
            return true;

        } catch (Exception e) {
            sendUnauthorizedResponse(request, response, "Failed to parse JWT: " + e.getMessage());
            return false;
        }
    }

    private void sendUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response, String message) throws Exception {
        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains("text/html")) {//temp for test
            String encodedMessage = java.net.URLEncoder.encode(message, StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/admin/pages/unauthorized.html?error=" + encodedMessage);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            String jsonResponse = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
            response.getWriter().write(jsonResponse);
        }
    }
}
