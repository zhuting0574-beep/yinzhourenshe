package com.wx.community.security;

import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminPasswordPolicyTest {
    @Test
    void acceptsStrongPasswordWithoutSequentialRuns() {
        assertDoesNotThrow(() -> AdminPasswordPolicy.validate("Qf!9mR@2vL"));
    }

    @Test
    void rejectsPasswordsShorterThanNineCharacters() {
        assertThrows(BusinessException.class, () -> AdminPasswordPolicy.validate("Aa!123xy"));
    }

    @Test
    void requiresUppercaseLowercaseAndSymbol() {
        assertThrows(BusinessException.class, () -> AdminPasswordPolicy.validate("onlylowercase!"));
        assertThrows(BusinessException.class, () -> AdminPasswordPolicy.validate("ONLYUPPERCASE!"));
        assertThrows(BusinessException.class, () -> AdminPasswordPolicy.validate("NoSymbol1234"));
    }

    @Test
    void rejectsAscendingAndDescendingLetterAndDigitRuns() {
        assertThrows(BusinessException.class, () -> AdminPasswordPolicy.validate("Abc!89Xyz"));
        assertThrows(BusinessException.class, () -> AdminPasswordPolicy.validate("Cba!98Xyz"));
        assertThrows(BusinessException.class, () -> AdminPasswordPolicy.validate("Xy!123Abcd"));
    }
}
