package cn.eldercare.identity;

import cn.eldercare.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthPolicyTest {

    @Test
    void usernameIsNormalizedToLowerCaseAndTrimmed() {
        assertEquals("family01", AuthService.normalizeUsername("  Family01 "));
        assertEquals("worker_01", AuthService.normalizeUsername("WORKER_01"));
        assertEquals("", AuthService.normalizeUsername(null));
    }

    @Test
    void usernameMustBeFourToThirtyTwoSafeCharacters() {
        AuthService.validateUsername("family01");
        AuthService.validateUsername("a_1b");
        assertEquals("USERNAME_INVALID", assertThrows(ApiException.class, () -> AuthService.validateUsername("abc")).code());
        assertEquals("USERNAME_INVALID", assertThrows(ApiException.class, () -> AuthService.validateUsername("a".repeat(33))).code());
        assertEquals("USERNAME_INVALID", assertThrows(ApiException.class, () -> AuthService.validateUsername("user-01")).code());
        assertEquals("USERNAME_INVALID", assertThrows(ApiException.class, () -> AuthService.validateUsername(null)).code());
    }

    @Test
    void passwordAllowsSixCharactersButRejectsOutsideRange() {
        AuthService.validatePassword("123456");
        AuthService.validatePassword("a".repeat(72));
        assertEquals("WEAK_PASSWORD", assertThrows(ApiException.class, () -> AuthService.validatePassword("12345")).code());
        assertEquals("WEAK_PASSWORD", assertThrows(ApiException.class, () -> AuthService.validatePassword("a".repeat(73))).code());
        assertEquals("WEAK_PASSWORD", assertThrows(ApiException.class, () -> AuthService.validatePassword(null)).code());
    }

    @Test
    void optionalEmailIsNormalizedAndValidatedOnlyWhenPresent() {
        assertNull(AuthService.normalizeEmail(null));
        assertNull(AuthService.normalizeEmail("   "));
        assertEquals("a@b.com", AuthService.normalizeEmail(" A@B.com "));
        assertEquals("EMAIL_INVALID", assertThrows(ApiException.class, () -> AuthService.normalizeEmail("not-an-email")).code());
    }
}
