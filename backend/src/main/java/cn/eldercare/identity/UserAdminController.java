package cn.eldercare.identity;

import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final JdbcTemplate jdbc;
    public UserAdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Reason(@NotBlank @Size(max = 500) String reason) { }

    @GetMapping
    public List<Map<String, Object>> users() {
        return jdbc.queryForList("SELECT id,username,display_name,email,status,created_at FROM account WHERE role='USER' ORDER BY id DESC LIMIT 100");
    }

    @PostMapping("/{id}/{action:disable|enable}")
    @Transactional
    public void status(@PathVariable long id, @PathVariable String action, @Valid @RequestBody Reason input, Authentication auth) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,status FROM account WHERE id=? AND role='USER' FOR UPDATE", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户账号不存在");
        String before = (String) rows.getFirst().get("status");
        String expected = "disable".equals(action) ? "ACTIVE" : "DISABLED";
        if (!expected.equals(before)) throw new ApiException(HttpStatus.CONFLICT, "USER_STATE_CONFLICT", "账号状态已变化");
        String after = "disable".equals(action) ? "DISABLED" : "ACTIVE";
        jdbc.update("UPDATE account SET status=? WHERE id=?", after, id);
        jdbc.update("INSERT INTO audit_event(actor_id,action,target_type,target_id,summary) VALUES(?,?,?,?,?)",
                Long.parseLong(auth.getName()), "USER_" + after, "ACCOUNT", id, input.reason().trim());
    }
}
