package cn.eldercare.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDatabaseGuardTest {
    @Test
    void acceptsOnlyExplicitAllowlistedTestSchema() {
        assertEquals("eldercare_test", TestDatabaseGuard.requireAllowedTarget(
                "jdbc:mysql://127.0.0.1:3306/eldercare_test?useUnicode=true", "eldercare_test"));
        assertEquals("eldercare_payment_test", TestDatabaseGuard.requireAllowedTarget(
                "jdbc:mysql://127.0.0.1:3306/eldercare_payment_test", "eldercare_payment_test"));
    }

    @Test
    void rejectsProductionAndUnexpectedTestSchemas() {
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.requireAllowedTarget(
                "jdbc:mysql://127.0.0.1:3306/eldercare", "eldercare_test"));
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.requireAllowedTarget(
                "jdbc:mysql://127.0.0.1:3306/other_test", "eldercare_test"));
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.requireAllowedTarget(
                "jdbc:mysql://db.example.com:3306/eldercare_test", "eldercare_test"));
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.requireAllowedTarget(
                "jdbc:mysql://127.0.0.1:3306/", "eldercare_test"));
    }
}
