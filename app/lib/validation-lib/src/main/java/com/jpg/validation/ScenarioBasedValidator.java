package com.jpg.validation;

import java.util.List;
import java.util.function.Predicate;

public abstract class ScenarioBasedValidator extends ConditionalValidator {
    protected abstract List<Scenario> getSupportedScenarios();

    private final boolean supportsScenario(Scenario scenario) {
        List<Scenario> scenarios = getSupportedScenarios();
        if (scenarios == null || scenario == null) return false;
        return scenarios.stream().anyMatch(s -> s.getScenarioName().equalsIgnoreCase(scenario.getScenarioName()));
    }

    @Override
    protected final Predicate<ValidationContext> getValidationPredicate() {
        return context -> {
            Object active = context.getAttribute(Scenario.SCENARIO_KEY);
            if (active instanceof Scenario scenario) {
                return supportsScenario(scenario);
            }
            return false;
        };
    }
}
