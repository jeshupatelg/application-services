package com.jpg.validation;

public interface Scenario extends Condition {
    String SCENARIO_KEY = "scenario";

    String getScenarioName();

    @Override
    default String getConditionKey() {
        return SCENARIO_KEY;
    }
}
