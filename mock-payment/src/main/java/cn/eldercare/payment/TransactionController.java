package cn.eldercare.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final JdbcTemplate jdbc;
    private final String serviceToken;
    private final String consoleToken;
    public TransactionController(JdbcTemplate jdbc, @Value("${app.service-token:}") String serviceToken,
                                 @Value("${app.console-token:}") String consoleToken) {
        this.jdbc = jdbc; this.serviceToken = serviceToken; this.consoleToken = consoleToken;
    }
    public record CreateRequest(@NotBlank String attemptRef, @NotNull Long orderRef,
                                @NotNull Integer settlementVersion, @NotNull @DecimalMin("0.00") BigDecimal amount) { }
    public record StatusRequest(@NotBlank String status) { }
    public record Transaction(String attemptRef, long orderRef, int settlementVersion, BigDecimal amount, String status) { }

    @GetMapping("/{ref}")
    public Transaction query(@PathVariable String ref, @RequestHeader(value="X-Service-Token",required=false) String token) {
        require(token, serviceToken);
        return get(ref);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Transaction create(@Valid @RequestBody CreateRequest request,
                              @RequestHeader(value="X-Console-Token",required=false) String token) {
        require(token, consoleToken);
        if (request.attemptRef().length() > 64 || request.orderRef() < 1 || request.settlementVersion() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "交易标识或版本无效");
        List<Transaction> existing = find(request.attemptRef());
        if (!existing.isEmpty()) {
            Transaction prior = existing.getFirst();
            if (prior.orderRef() != request.orderRef() || prior.settlementVersion() != request.settlementVersion()
                    || prior.amount().compareTo(request.amount()) != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "交易标识与已有交易不一致");
            return prior;
        }
        jdbc.update("INSERT INTO simulated_transaction(attempt_ref,order_ref,settlement_version,amount,status) VALUES(?,?,?,?,'PROCESSING')",
                request.attemptRef(),request.orderRef(),request.settlementVersion(),request.amount());
        Long id = jdbc.queryForObject("SELECT id FROM simulated_transaction WHERE attempt_ref=?",Long.class,request.attemptRef());
        jdbc.update("INSERT INTO simulated_transaction_event(transaction_id,old_status,new_status,actor_label) VALUES(?,NULL,'PROCESSING','CONSOLE')",id);
        return get(request.attemptRef());
    }

    @PostMapping("/{ref}/status")
    @Transactional
    public Transaction decide(@PathVariable String ref, @Valid @RequestBody StatusRequest request,
                              @RequestHeader(value="X-Console-Token",required=false) String token) {
        require(token, consoleToken);
        if (!List.of("SUCCESS","FAILED","CANCELLED").contains(request.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标状态无效");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,status FROM simulated_transaction WHERE attempt_ref=? FOR UPDATE",ref);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "交易未登记");
        Map<String,Object> row = rows.getFirst();
        if (!"PROCESSING".equals(row.get("status"))) {
            if (request.status().equals(row.get("status"))) return get(ref);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "终态交易不可变更");
        }
        jdbc.update("UPDATE simulated_transaction SET status=? WHERE id=?",request.status(),row.get("id"));
        jdbc.update("INSERT INTO simulated_transaction_event(transaction_id,old_status,new_status,actor_label) VALUES(?,'PROCESSING',?,'CONSOLE')",row.get("id"),request.status());
        return get(ref);
    }

    private Transaction get(String ref) {
        List<Transaction> rows = find(ref);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "交易未登记");
        return rows.getFirst();
    }
    private List<Transaction> find(String ref) {
        return jdbc.query("SELECT attempt_ref,order_ref,settlement_version,amount,status FROM simulated_transaction WHERE attempt_ref=?",
                (row,n)->new Transaction(row.getString(1),row.getLong(2),row.getInt(3),row.getBigDecimal(4),row.getString(5)),ref);
    }
    private void require(String actual,String expected) {
        if (expected == null || expected.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"服务令牌尚未配置");
        if (actual == null || !MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"令牌无效");
    }
}
