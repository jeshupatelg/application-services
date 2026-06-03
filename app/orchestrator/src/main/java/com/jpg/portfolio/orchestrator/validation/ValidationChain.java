package com.jpg.portfolio.orchestrator.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ValidationChain {

    private final List<ValidationStep> steps;

    @Autowired
    public ValidationChain(List<ValidationStep> steps) {
        // Automatically sorted by Spring based on @Order annotations
        this.steps = steps;
    }

    public ValidationResult validate(ValidationContext context, ValidationScenario scenario) {
        for (ValidationStep step : steps) {
            if (step.supports(scenario)) {
                ValidationResult result = step.validate(context);
                if (!result.isValid()) {
                    return result;
                }
            }
        }
        return ValidationResult.success();
    }
}
