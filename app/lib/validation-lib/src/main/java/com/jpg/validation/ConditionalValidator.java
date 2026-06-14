package com.jpg.validation;

import java.util.function.Predicate;

public abstract class ConditionalValidator implements Validator {
    protected abstract Predicate<ValidationContext> getValidationPredicate();
    protected abstract ValidationResult doValidate(ValidationContext context);

    @Override
    public final ValidationResult validate(ValidationContext context) {
        if (getValidationPredicate().test(context)) {
            return doValidate(context);
        }
        return ValidationResult.success();
    }
}
