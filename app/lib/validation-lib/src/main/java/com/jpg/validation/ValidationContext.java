package com.jpg.validation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ValidationContext {
    private final String username;
    private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

    public ValidationContext(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        if (value == null) {
            return;
        }
        // Throws exception if key already exists to prevent mutations
        Object existing = attributes.putIfAbsent(key, value);
        if (existing != null) {
            throw new IllegalStateException("Attribute '" + key + "' is already defined and cannot be modified.");
        }
    }
}
