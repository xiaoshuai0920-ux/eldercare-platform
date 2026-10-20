package cn.eldercare.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
                          @Value("${app.bootstrap-admin-email:}") String email,
                          @Value("${app.bootstrap-admin-password:}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 12) throw new IllegalStateException("管理员初始化邮箱与至少 12 位密码必须同时提供");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE role='ADMIN'", Integer.class);
        if (count != null && count == 0) {
            jdbc.update("INSERT INTO account(email,display_name,password_hash,role,status) VALUES(?,?,?,?,?)",
                    email.trim().toLowerCase(java.util.Locale.ROOT), "系统管理员", encoder.encode(password), "ADMIN", "ACTIVE");
        }
    }
}
