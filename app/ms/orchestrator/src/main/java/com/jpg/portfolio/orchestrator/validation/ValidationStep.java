package com.jpg.portfolio.orchestrator.validation;

public interface ValidationStep {
    ValidationResult validate(ValidationContext context);
    boolean supports(ValidationScenario scenario);
}
