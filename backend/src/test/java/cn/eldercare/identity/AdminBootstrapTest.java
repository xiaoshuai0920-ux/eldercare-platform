package cn.eldercare.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBootstrapTest {
    @Test
    void createsAdminWhenUsernameDoesNotExist() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate(List.of());

        bootstrap(jdbc, false).run(new DefaultApplicationArguments());

        assertTrue(jdbc.sql.stream().anyMatch(sql -> sql.startsWith("INSERT INTO account")));
        assertFalse(jdbc.sql.stream().anyMatch(sql -> sql.startsWith("UPDATE account")));
        assertEquals("bcrypt:initial-secret", jdbc.parameters.getFirst()[3]);
    }

    @Test
    void doesNotResetExistingAdminByDefault() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate(List.of(Map.of("id", 7L)));

        bootstrap(jdbc, false).run(new DefaultApplicationArguments());

        assertTrue(jdbc.sql.isEmpty());
    }

    @Test
    void resetsExistingAdminOnlyWhenExplicitlyEnabled() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate(List.of(Map.of("id", 7L)));

        bootstrap(jdbc, true).run(new DefaultApplicationArguments());

        assertEquals(1, jdbc.sql.size());
        assertTrue(jdbc.sql.getFirst().startsWith("UPDATE account SET password_hash"));
        assertEquals("bcrypt:initial-secret", jdbc.parameters.getFirst()[0]);
        assertEquals(7L, jdbc.parameters.getFirst()[1]);
    }

    private static AdminBootstrap bootstrap(FakeJdbcTemplate jdbc, boolean reset) {
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "bcrypt:" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals(encode(rawPassword));
            }
        };
        return new AdminBootstrap(jdbc, encoder, "admin", "initial-secret", reset);
    }

    private static final class FakeJdbcTemplate extends JdbcTemplate {
        private final List<Map<String, Object>> admins;
        private final List<String> sql = new ArrayList<>();
        private final List<Object[]> parameters = new ArrayList<>();

        private FakeJdbcTemplate(List<Map<String, Object>> admins) {
            this.admins = admins;
        }

        @Override
        public List<Map<String, Object>> queryForList(String query, Object... args) {
            if (!query.startsWith("SELECT id FROM account WHERE username=?")) {
                throw new AssertionError("Unexpected query: " + query);
            }
            return admins;
        }

        @Override
        public int update(String query, Object... args) {
            sql.add(query);
            parameters.add(args);
            return 1;
        }
    }
}
