package cn.eldercare.settlement;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import cn.eldercare.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementPaymentFailureTest {
    private static final long ORDER_ID = 77;
    private static final long SETTLEMENT_ID = 88;
    private static final long ATTEMPT_ID = 99;

    @Test
    void connectionFailureReturns503AndLeavesAttemptAndSettlementPending() {
        InMemoryJdbcTemplate jdbc = new InMemoryJdbcTemplate();
        SettlementController controller = controller(jdbc, (url, token, ref) -> { throw new IOException("connection refused"); }, true);

        ApiException error = assertThrows(ApiException.class, () -> controller.sync(ORDER_ID, admin()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        assertPending(jdbc);
        assertEquals(0, jdbc.updateCount);
    }

    @Test
    void remoteErrorReturns503AndOfflineSettlementRemainsBlocked() {
        InMemoryJdbcTemplate jdbc = new InMemoryJdbcTemplate();
        PaymentQueryClient remoteError = (url, token, ref) -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_QUERY_UNAVAILABLE", "remote 503");
        };
        SettlementController controller = controller(jdbc, remoteError, true);

        ApiException error = assertThrows(ApiException.class, () -> controller.sync(ORDER_ID, admin()));
        ApiException offline = assertThrows(ApiException.class, () -> controller.offline(ORDER_ID, admin()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        assertEquals(HttpStatus.CONFLICT, offline.status());
        assertEquals("SETTLEMENT_STATE_CONFLICT", offline.code());
        assertPending(jdbc);
        assertEquals(0, jdbc.updateCount);
    }

    @Test
    void timeoutReturns503WithoutChangingSettlementOrPaymentAttempt() {
        InMemoryJdbcTemplate jdbc = new InMemoryJdbcTemplate();
        PaymentQueryClient timeout = (url, token, ref) -> { throw new HttpTimeoutException("query timeout"); };
        SettlementController controller = controller(jdbc, timeout, true);

        ApiException error = assertThrows(ApiException.class, () -> controller.sync(ORDER_ID, admin()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
        assertPending(jdbc);
        assertEquals(0, jdbc.updateCount);
    }

    @Test
    void returns503BeforeDatabaseAccessWhenMockPaymentIsDisabled() {
        SettlementController controller = controller(new JdbcTemplate(), (url, token, ref) -> {
            throw new AssertionError("disabled mock payment must not query the service");
        }, false);

        ApiException error = assertThrows(ApiException.class, () -> controller.sync(ORDER_ID, admin()));

        assertEquals("MOCK_PAYMENT_DISABLED", error.code());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status());
    }

    @Test
    void canRetryAfterPaymentServiceRecovers() {
        InMemoryJdbcTemplate jdbc = new InMemoryJdbcTemplate();
        AtomicInteger calls = new AtomicInteger();
        PaymentQueryClient recovering = (url, token, ref) -> {
            if (calls.incrementAndGet() == 1) throw new IOException("temporary connection failure");
            return new PaymentQueryClient.Remote("SUCCESS", ORDER_ID, 2, new BigDecimal("28.00"));
        };
        SettlementController controller = controller(jdbc, recovering, true);

        assertThrows(ApiException.class, () -> controller.sync(ORDER_ID, admin()));
        assertPending(jdbc);
        SettlementController.View result = controller.sync(ORDER_ID, admin());

        assertEquals("SETTLED", result.settlement().get("status"));
        assertEquals("SUCCESS", result.attempts().getFirst().get("remote_status"));
        assertEquals(2, calls.get());
    }

    private static SettlementController controller(JdbcTemplate jdbc, PaymentQueryClient paymentClient, boolean enabled) {
        return new SettlementController(new SettlementRepository(jdbc), null, null, paymentClient,
                "http://payment.local", "service-token", enabled);
    }

    private static UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken("5", "test", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static void assertPending(InMemoryJdbcTemplate jdbc) {
        assertEquals("PROCESSING", jdbc.settlement.get("status"));
        assertEquals("UNREGISTERED", jdbc.attempt.get("remote_status"));
    }

    private static final class InMemoryJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object> settlement = new HashMap<>();
        private final Map<String, Object> attempt = new HashMap<>();
        private int updateCount;

        private InMemoryJdbcTemplate() {
            settlement.put("id", SETTLEMENT_ID);
            settlement.put("order_id", ORDER_ID);
            settlement.put("amount", new BigDecimal("28.00"));
            settlement.put("version", 2);
            settlement.put("method", "SIMULATED");
            settlement.put("status", "PROCESSING");
            settlement.put("settled_at", null);
            attempt.put("id", ATTEMPT_ID);
            attempt.put("attempt_ref", "attempt-99");
            attempt.put("settlement_version", 2);
            attempt.put("amount", new BigDecimal("28.00"));
            attempt.put("remote_status", "UNREGISTERED");
            attempt.put("synced_at", null);
            attempt.put("created_at", LocalDateTime.now());
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.startsWith("SELECT * FROM settlement WHERE order_id=?")) return List.of(settlement);
            if (sql.startsWith("SELECT * FROM payment_attempt WHERE settlement_id=?")) return List.of(attempt);
            if (sql.startsWith("SELECT attempt_ref,amount,settlement_version,remote_status,created_at,synced_at FROM payment_attempt")) return List.of(attempt);
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.startsWith("SELECT COUNT(*) FROM payment_attempt")) return (T) Integer.valueOf(1);
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            if (sql.startsWith("UPDATE payment_attempt SET remote_status=?")) {
                attempt.put("remote_status", args[0]);
                attempt.put("synced_at", args[1]);
                return 1;
            }
            if (sql.startsWith("UPDATE settlement SET status='SETTLED'")) {
                settlement.put("status", "SETTLED");
                settlement.put("method", "SIMULATED");
                settlement.put("settled_at", args[0]);
                return 1;
            }
            if (sql.startsWith("INSERT INTO settlement_event")) return 1;
            throw new AssertionError("Unexpected update: " + sql);
        }
    }
}
