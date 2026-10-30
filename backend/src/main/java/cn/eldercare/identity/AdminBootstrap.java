package cn.eldercare.identity;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 管理员账号初始化：按 username 判断是否存在，必要时按配置重置密码。
 * 先于演示数据 seeder 执行（order = 10）。
 */
@Component
public class AdminBootstrap implements ApplicationRunner, Ordered {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final boolean reset;

    public AdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
                          @Value("${app.bootstrap-admin-username:admin}") String username,
                          @Value("${app.bootstrap-admin-password:}") String password,
                          @Value("${app.bootstrap-admin-reset:false}") boolean reset) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
        this.reset = reset;
    }

    @Override
    public int getOrder() { return 10; }

    @Override
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.info("管理员初始化跳过：未配置 app.bootstrap-admin-username/password");
            return;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        AuthService.validateUsername(normalized);
        if (password.length() < 6 || password.length() > 72) {
            throw new IllegalStateException("管理员初始密码长度须为 6–72 位");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM account WHERE username=?", normalized);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO account(username,email,display_name,password_hash,role,status,accepting) VALUES(?,?,?,?,?,?,1)",
                    normalized, null, "系统管理员", encoder.encode(password), "ADMIN", "ACTIVE");
            log.info("管理员账号已创建 username={}", normalized);
        } else if (reset) {
            jdbc.update("UPDATE account SET password_hash=?,status='ACTIVE',accepting=1,failed_logins=0,locked_until=NULL WHERE id=?",
                    encoder.encode(password), rows.getFirst().get("id"));
            log.warn("管理员密码已按临时配置重置 username={}", normalized);
        } else {
            log.info("管理员账号已存在，未重置密码 username={}", normalized);
        }
    }
}
