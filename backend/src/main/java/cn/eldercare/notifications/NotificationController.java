package cn.eldercare.notifications;

import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.PageQuery;
import cn.eldercare.common.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) { this.notifications = notifications; }

    @GetMapping
    public PageResult<Map<String, Object>> list(Authentication auth, @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String kind) {
        PageQuery query = PageQuery.of(page, size);
        long accountId = Long.parseLong(auth.getName());
        String visibility = visibilityOf(auth);
        String kindClause = notifications.kindClause(kind);
        return PageResult.of(notifications.page(accountId, visibility, kindClause, query.size(), query.offset()),
                query, notifications.count(accountId, visibility, kindClause));
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable long id, Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        if (!notifications.existsFor(id, actor, visibilityOf(auth))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "消息不存在或当前无权查看");
        }
        notifications.markRead(id, actor);
    }

    /** 角色到可见性档位的映射；SQL 本身由仓储持有 */
    private String visibilityOf(Authentication auth) {
        if (hasRole(auth, "ROLE_ADMIN")) return NotificationRepository.VISIBLE_ADMIN;
        if (hasRole(auth, "ROLE_WORKER")) return NotificationRepository.VISIBLE_WORKER;
        return NotificationRepository.VISIBLE_USER;
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
