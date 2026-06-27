package com.jpg.validation;

import com.jpg.validation.exception.ValidationException;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ValidationChain {

    private final List<Validator> validators;

    public ValidationChain(List<Validator> validators) {
        this.validators = validators;
    }

    public void validate(ValidationContext context) {
        for (Validator validator : validators) {
            ValidationResult result = validator.validate(context);
            if (!result.isValid()) {
                throw new ValidationException(result);
            }
        }
    }
}
