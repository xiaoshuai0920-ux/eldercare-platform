package cn.eldercare.payment;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "PROCESS_UP", "service", "mock-payment");
    }
    @GetMapping("/ready")
    public ResponseEntity<Map<String,String>> ready(){
        try {jdbc.queryForObject("SELECT COUNT(*) FROM simulated_transaction",Long.class);return ResponseEntity.ok(Map.of("status","READY","service","mock-payment"));}
        catch(Exception error){return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status","DATABASE_UNAVAILABLE","service","mock-payment"));}
    }
}
