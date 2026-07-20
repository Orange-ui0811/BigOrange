package com.orangejd.server.validation;

import com.orangejd.pojo.dto.RegisterDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterDTOValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void rejectsRegistrationWhenLegalConsentIsMissing() {
        RegisterDTO dto = validRegistration();
        dto.setLegalAccepted(null);

        assertTrue(hasLegalAcceptedViolation(validator.validate(dto)));
    }

    @Test
    void rejectsRegistrationWhenLegalConsentIsFalse() {
        RegisterDTO dto = validRegistration();
        dto.setLegalAccepted(false);

        assertTrue(hasLegalAcceptedViolation(validator.validate(dto)));
    }

    @Test
    void acceptsRegistrationWhenCurrentLegalDocumentsAreAccepted() {
        RegisterDTO dto = validRegistration();

        assertFalse(hasLegalAcceptedViolation(validator.validate(dto)));
    }

    private static boolean hasLegalAcceptedViolation(Set<ConstraintViolation<RegisterDTO>> violations) {
        return violations.stream().anyMatch(violation ->
                "legalAccepted".equals(violation.getPropertyPath().toString()));
    }

    private static RegisterDTO validRegistration() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("new-user");
        dto.setPassword("orange123");
        dto.setNickname("Orange User");
        dto.setEmail("user@example.com");
        dto.setCode("123456");
        dto.setTurnstileToken("test-token");
        dto.setLegalAccepted(true);
        dto.setLegalVersion("2026-07-19");
        return dto;
    }
}
