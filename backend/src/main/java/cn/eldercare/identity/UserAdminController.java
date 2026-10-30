package cn.eldercare.identity;

import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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
    private final UserAdminRepository users;
    private final AuditService audit;

    public UserAdminController(UserAdminRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    public record Reason(@NotBlank @Size(max = 500) String reason) { }

    @GetMapping
    public List<Map<String, Object>> users() {
        return users.users();
    }

    @PostMapping("/{id}/{action:disable|enable}")
    @Transactional
    public void status(@PathVariable long id, @PathVariable String action, @Valid @RequestBody Reason input,
                       Authentication auth) {
        Map<String, Object> user = users.userForUpdate(id);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户账号不存在");
        boolean disabling = "disable".equals(action);
        String expected = disabling ? "ACTIVE" : "DISABLED";
        if (!expected.equals(user.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "USER_STATE_CONFLICT", "账号状态已变化");
        String after = disabling ? "DISABLED" : "ACTIVE";
        users.updateStatus(id, after);
        audit.record(Long.parseLong(auth.getName()), "USER_" + after, "ACCOUNT", id,
                (disabling ? "停用用户账号：" : "启用用户账号：") + input.reason().trim());
    }
}
