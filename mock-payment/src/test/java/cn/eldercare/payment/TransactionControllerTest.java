package cn.eldercare.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionControllerTest {
    private final InMemoryJdbcTemplate jdbc = new InMemoryJdbcTemplate();
    private final TransactionController controller = new TransactionController(jdbc, "service-token", "console-token");

    @Test
    void createsTransactionAndReusesSameAttemptReference() {
        var request = request("attempt-1", "25.00");

        var created = controller.create(request, "console-token");
        var repeated = controller.create(request, "console-token");

        assertEquals("PROCESSING", created.status());
        assertEquals(created, repeated);
        assertEquals(1, jdbc.transactions.size());
    }

    @Test
    void rejectsAttemptReferenceReusedWithDifferentPayload() {
        controller.create(request("attempt-1", "25.00"), "console-token");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.create(request("attempt-1", "26.00"), "console-token"));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void rejectsDifferentAmountForSameOrderVersionEvenWithNewAttemptReference() {
        controller.create(request("attempt-1", "25.00"), "console-token");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.create(request("attempt-2", "26.00"), "console-token"));

        assertEquals(409, error.getStatusCode().value());
        assertEquals(1, jdbc.transactions.size());
    }

    @Test
    void reusesProcessingOrSuccessfulTransactionForSameOrderVersion() {
        var created = controller.create(request("attempt-1", "25.00"), "console-token");

        var repeated = controller.create(request("attempt-2", "25.00"), "console-token");

        assertEquals(created, repeated);
        assertEquals(1, jdbc.transactions.size());

        controller.decide("attempt-1", new TransactionController.StatusRequest("SUCCESS"), "console-token");
        var successfulRetry = controller.create(request("attempt-3", "25.00"), "console-token");

        assertEquals("SUCCESS", successfulRetry.status());
        assertEquals("attempt-1", successfulRetry.attemptRef());
        assertEquals(1, jdbc.transactions.size());
    }

    @Test
    void permitsNewAttemptAfterFailedTransaction() {
        controller.create(request("attempt-1", "25.00"), "console-token");
        controller.decide("attempt-1", new TransactionController.StatusRequest("FAILED"), "console-token");

        var retry = controller.create(request("attempt-2", "25.00"), "console-token");

        assertEquals("attempt-2", retry.attemptRef());
        assertEquals(2, jdbc.transactions.size());
    }

    @Test
    void rejectsChangingTerminalTransactionButAllowsIdempotentSameStatus() {
        controller.create(request("attempt-1", "25.00"), "console-token");
        var settled = controller.decide("attempt-1", new TransactionController.StatusRequest("SUCCESS"), "console-token");

        assertEquals(settled, controller.decide("attempt-1", new TransactionController.StatusRequest("SUCCESS"), "console-token"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.decide("attempt-1", new TransactionController.StatusRequest("FAILED"), "console-token"));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void returnsNotFoundForUnknownTransaction() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.query("unknown", "service-token"));

        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void rejectsMissingOrWrongRequestTokenAndMissingServerConfiguration() {
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> controller.query("unknown", null));
        ResponseStatusException wrong = assertThrows(ResponseStatusException.class,
                () -> controller.query("unknown", "wrong-token"));
        ResponseStatusException missingConsole = assertThrows(ResponseStatusException.class,
                () -> controller.create(request("attempt-1", "25.00"), null));
        ResponseStatusException wrongConsole = assertThrows(ResponseStatusException.class,
                () -> controller.create(request("attempt-1", "25.00"), "wrong-token"));
        ResponseStatusException unconfigured = assertThrows(ResponseStatusException.class,
                () -> new TransactionController(jdbc, "", "console-token").query("unknown", "service-token"));

        assertEquals(401, missing.getStatusCode().value());
        assertEquals(401, wrong.getStatusCode().value());
        assertEquals(401, missingConsole.getStatusCode().value());
        assertEquals(401, wrongConsole.getStatusCode().value());
        assertEquals(503, unconfigured.getStatusCode().value());
    }

    @Test
    void returnsConcurrentWinnerWhenUniqueActiveOrderVersionKeyConflicts() {
        var winner = new TransactionController.Transaction("attempt-winner", 12, 3,
                new BigDecimal("25.00"), "PROCESSING");
        jdbc.duplicateNextInsert = winner;

        var result = controller.create(request("attempt-loser", "25.00"), "console-token");

        assertEquals(winner, result);
        assertEquals(1, jdbc.transactions.size());
    }

    private static TransactionController.CreateRequest request(String attemptRef, String amount) {
        return new TransactionController.CreateRequest(attemptRef, 12L, 3, new BigDecimal(amount));
    }

    private static final class InMemoryJdbcTemplate extends JdbcTemplate {
        private final Map<String, TransactionController.Transaction> transactions = new HashMap<>();
        private final Map<String, Long> ids = new HashMap<>();
        private long nextId = 1;
        private TransactionController.Transaction duplicateNextInsert;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            TransactionController.Transaction result;
            if (sql.contains("WHERE attempt_ref=?")) {
                result = transactions.get((String) args[0]);
            } else if (sql.contains("active_order_version=?")) {
                String key = (String) args[0];
                result = transactions.values().stream()
                        .filter(transaction -> activeKey(transaction).equals(key))
                        .findFirst().orElse(null);
            } else {
                throw new AssertionError("Unexpected query: " + sql);
            }
            return result == null ? List.of() : (List<T>) List.of(result);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (!sql.contains("FROM simulated_transaction WHERE attempt_ref=? FOR UPDATE")) {
                throw new AssertionError("Unexpected query: " + sql);
            }
            String attemptRef = (String) args[0];
            TransactionController.Transaction transaction = transactions.get(attemptRef);
            if (transaction == null) return List.of();
            return List.of(Map.of("id", ids.get(attemptRef), "status", transaction.status()));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (!sql.startsWith("SELECT id FROM simulated_transaction WHERE attempt_ref=?")) {
                throw new AssertionError("Unexpected query: " + sql);
            }
            return (T) ids.get((String) args[0]);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO simulated_transaction(")) {
                if (duplicateNextInsert != null) {
                    TransactionController.Transaction winner = duplicateNextInsert;
                    duplicateNextInsert = null;
                    store(winner);
                    throw new DuplicateKeyException("active order version already exists");
                }
                store(new TransactionController.Transaction((String) args[0], ((Number) args[1]).longValue(),
                        ((Number) args[2]).intValue(), (BigDecimal) args[3], "PROCESSING"));
                return 1;
            }
            if (sql.startsWith("UPDATE simulated_transaction SET status=? WHERE id=?")) {
                String status = (String) args[0];
                long id = ((Number) args[1]).longValue();
                transactions.replaceAll((ref, transaction) -> ids.get(ref) == id
                        ? new TransactionController.Transaction(ref, transaction.orderRef(), transaction.settlementVersion(),
                        transaction.amount(), status) : transaction);
                return 1;
            }
            if (sql.startsWith("INSERT INTO simulated_transaction_event")) return 1;
            throw new AssertionError("Unexpected update: " + sql);
        }

        private void store(TransactionController.Transaction transaction) {
            transactions.put(transaction.attemptRef(), transaction);
            ids.put(transaction.attemptRef(), nextId++);
        }

        private static String activeKey(TransactionController.Transaction transaction) {
            if (!List.of("PROCESSING", "SUCCESS").contains(transaction.status())) return "";
            return transaction.orderRef() + ":" + transaction.settlementVersion();
        }
    }
}
