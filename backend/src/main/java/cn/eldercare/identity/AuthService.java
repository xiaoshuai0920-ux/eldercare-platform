package cn.eldercare.identity;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.SqlValues;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final SecureRandom random = new SecureRandom();
    private final AccountMapper accounts;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SmtpMailer mailer;
    private final String emailMode;

    public AuthService(AccountMapper accounts, JdbcTemplate jdbc, PasswordEncoder encoder, SmtpMailer mailer,
                       @Value("${app.email-mode:demo}") String emailMode) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mailer = mailer;
        this.emailMode = emailMode;
    }

    public record SessionUser(Long id, String name, String role) { }
    public record VerificationResult(String mode, String message) { }

    public VerificationResult requestCode(String inputEmail) {
        String email = normalizeEmail(inputEmail);
        if (findByEmail(email) != null) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "该邮箱已注册");
        Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM email_verification WHERE email=? AND created_at > ?", Integer.class,
                email, now().minusHours(1));
        if (recent != null && recent >= 5) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CODE_LIMIT", "验证码请求过于频繁，请稍后再试");
        String code = String.format("%06d", random.nextInt(1_000_000));
        if (!emailMode.equalsIgnoreCase("smtp") && !emailMode.equalsIgnoreCase("demo")) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_MODE_INVALID", "邮件验证模式未正确配置");
        }
        String mode = emailMode.equalsIgnoreCase("smtp") ? "SMTP" : "DEMO";
        if (mode.equals("SMTP")) mailer.sendVerificationCode(email, code);
        jdbc.update("INSERT INTO email_verification(email,code_hash,mode,expires_at) VALUES(?,?,?,?)",
                email, encoder.encode(code), mode, now().plusMinutes(10));
        return mode.equals("SMTP")
                ? new VerificationResult("SMTP", "验证码已提交至邮件服务器。请在邮箱中查收。")
                : new VerificationResult("DEMO", "本地演示验证码：" + code + "。此模式不验证邮箱所有权。");
    }

    @Transactional
    public void register(String name, String inputEmail, String code, String password) {
        String email = normalizeEmail(inputEmail);
        if (findByEmail(email) != null) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "该邮箱已注册");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,code_hash,expires_at,consumed_at FROM email_verification WHERE email=? ORDER BY id DESC LIMIT 1 FOR UPDATE", email);
        if (rows.isEmpty() || rows.getFirst().get("consumed_at") != null
                || SqlValues.localDateTime(rows.getFirst().get("expires_at")).isBefore(now())
                || !encoder.matches(code, (String) rows.getFirst().get("code_hash"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CODE", "验证码无效或已过期");
        }
        if (password.length() < 8 || password.length() > 72) throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码长度须为 8–72 位");
        AccountEntity account = new AccountEntity();
        account.setEmail(email);
        account.setDisplayName(name.trim());
        account.setPasswordHash(encoder.encode(password));
        account.setRole("USER");
        account.setStatus("ACTIVE");
        accounts.insert(account);
        jdbc.update("UPDATE email_verification SET consumed_at=? WHERE id=?", now(), rows.getFirst().get("id"));
        jdbc.update("INSERT INTO audit_event(actor_id,action,target_type,target_id,summary) VALUES(?,?,?,?,?)",
                account.getId(), "REGISTER", "ACCOUNT", account.getId(), "普通用户注册");
    }

    public SessionUser login(String inputEmail, String password, HttpServletRequest request, HttpServletResponse response) {
        String email = normalizeEmail(inputEmail);
        AccountEntity account = findByEmail(email);
        if (account == null) throw badCredentials();
        List<Map<String, Object>> info = jdbc.queryForList("SELECT failed_logins,locked_until FROM account WHERE id=?", account.getId());
        if (!info.isEmpty() && info.getFirst().get("locked_until") != null
                && SqlValues.localDateTime(info.getFirst().get("locked_until")).isAfter(now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED", "登录尝试过多，请稍后再试");
        }
        if (!"ACTIVE".equals(account.getStatus()) || !encoder.matches(password, account.getPasswordHash())) {
            jdbc.update("UPDATE account SET failed_logins=failed_logins+1, locked_until=CASE WHEN failed_logins+1>=5 THEN ? ELSE locked_until END WHERE id=?",
                    now().plusMinutes(15), account.getId());
            throw badCredentials();
        }
        jdbc.update("UPDATE account SET failed_logins=0,locked_until=NULL WHERE id=?", account.getId());
        if (request.getSession(false) != null) request.changeSessionId();
        else request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(String.valueOf(account.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole()))));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return new SessionUser(account.getId(), account.getDisplayName(), account.getRole());
    }

    public SessionUser current(Long id) {
        AccountEntity account = accounts.selectById(id);
        if (account == null || !"ACTIVE".equals(account.getStatus())) throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "账号已失效");
        return new SessionUser(account.getId(), account.getDisplayName(), account.getRole());
    }

    private AccountEntity findByEmail(String email) {
        return accounts.selectOne(new LambdaQueryWrapper<AccountEntity>().eq(AccountEntity::getEmail, email));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private LocalDateTime now() { return LocalDateTime.now(BUSINESS_ZONE); }
    private ApiException badCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "邮箱或密码错误"); }
}
