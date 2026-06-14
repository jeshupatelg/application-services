package com.jpg.validation.exception;

import com.jpg.validation.ValidationResult;

public class ValidationException extends RuntimeException {
    private final ValidationResult result;

    public ValidationException(ValidationResult result) {
        super(result.getReason());
        this.result = result;
    }

    public ValidationException(String message) {
        super(message);
        this.result = ValidationResult.fail(message);
    }

    public ValidationResult getResult() {
        return result;
    }
}
