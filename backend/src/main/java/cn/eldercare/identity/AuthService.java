package cn.eldercare.identity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.SqlValues;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{4,32}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD = 6;
    private static final int MAX_PASSWORD = 72;

    private final AccountMapper accounts;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AuthService(AccountMapper accounts, JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public record SessionUser(Long id, String username, String name, String role) { }

    @Transactional
    public void register(String inputUsername, String name, String rawEmail, String password) {
        String username = normalizeUsername(inputUsername);
        validateUsername(username);
        validatePassword(password);
        if (findByUsername(username) != null) throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该账号已被注册");
        String email = normalizeEmail(rawEmail);
        if (email != null && findByEmail(email) != null) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "该邮箱已被使用");
        String displayName = name == null || name.isBlank() ? username : name.trim();
        AccountEntity account = new AccountEntity();
        account.setUsername(username);
        account.setEmail(email);
        account.setDisplayName(displayName);
        account.setPasswordHash(encoder.encode(password));
        account.setRole("USER");
        account.setStatus("ACTIVE");
        accounts.insert(account);
        jdbc.update("INSERT INTO audit_event(actor_id,action,target_type,target_id,summary) VALUES(?,?,?,?,?)",
                account.getId(), "REGISTER", "ACCOUNT", account.getId(), "普通用户注册");
    }

    public SessionUser login(String inputUsername, String password, HttpServletRequest request, HttpServletResponse response) {
        if (password == null || password.isBlank()) throw badCredentials();
        AccountEntity account = findByUsername(normalizeUsername(inputUsername));
        if (account == null) throw badCredentials();
        List<Map<String, Object>> info = jdbc.queryForList("SELECT failed_logins,locked_until FROM account WHERE id=?", account.getId());
        if (!info.isEmpty() && info.getFirst().get("locked_until") != null
                && SqlValues.localDateTime(info.getFirst().get("locked_until")).isAfter(now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED", "登录尝试过多，请稍后再试");
        }
        if (!"ACTIVE".equals(account.getStatus()) || !encoder.matches(password, account.getPasswordHash())) {
            // MySQL 的 SET 自左向右求值：先判断是否达到阈值，再自增，避免用自增后的值比较导致提前锁定
            jdbc.update("UPDATE account SET locked_until=CASE WHEN failed_logins+1>=5 THEN ? ELSE locked_until END, failed_logins=failed_logins+1 WHERE id=?",
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
        return toSession(account);
    }

    public SessionUser current(Long id) {
        AccountEntity account = accounts.selectById(id);
        if (account == null || !"ACTIVE".equals(account.getStatus())) throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "账号已失效");
        return toSession(account);
    }

    @Transactional
    public void changePassword(long accountId, String currentPassword, String newPassword) {
        AccountEntity account = accounts.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "账号已失效");
        if (currentPassword == null || !encoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        validatePassword(newPassword);
        jdbc.update("UPDATE account SET password_hash=?,failed_logins=0,locked_until=NULL WHERE id=?",
                encoder.encode(newPassword), accountId);
        jdbc.update("INSERT INTO audit_event(actor_id,action,target_type,target_id,summary) VALUES(?,?,?,?,?)",
                accountId, "PASSWORD_CHANGE", "ACCOUNT", accountId, "账号自行修改密码");
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码长度须为 6–72 位");
        }
    }

    public static void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USERNAME_INVALID", "账号须为 4–32 位小写字母、数字或下划线");
        }
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private AccountEntity findByUsername(String username) {
        if (username == null || username.isBlank()) return null;
        return accounts.selectOne(new LambdaQueryWrapper<AccountEntity>().eq(AccountEntity::getUsername, username));
    }

    private AccountEntity findByEmail(String email) {
        return accounts.selectOne(new LambdaQueryWrapper<AccountEntity>().eq(AccountEntity::getEmail, email));
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_INVALID", "邮箱格式不正确");
        }
        return normalized;
    }

    private SessionUser toSession(AccountEntity account) {
        return new SessionUser(account.getId(), account.getUsername(), account.getDisplayName(), account.getRole());
    }

    private LocalDateTime now() { return LocalDateTime.now(BUSINESS_ZONE); }
    private ApiException badCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "账号或密码错误"); }
}
