package cn.eldercare.notifications;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.PermissionColumn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
@PreAuthorize("hasRole('USER')")
public class ReminderController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReminderRepository reminders;
    private final FamilyService family;

    public ReminderController(ReminderRepository reminders, FamilyService family) {
        this.reminders = reminders;
        this.family = family;
    }

    public record Create(@NotNull Long elderId, @NotBlank @Size(max = 100) String title,
                         @NotBlank String recurrence, @NotNull LocalDateTime firstAt) { }
    public record Update(@NotBlank @Size(max = 100) String title,
                         @NotBlank String recurrence, @NotNull LocalDateTime nextFireAt) { }
    public record Resume(@NotNull LocalDateTime nextFireAt) { }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        return reminders.list(Long.parseLong(auth.getName()));
    }

    @PostMapping
    @Transactional
    public void create(@Valid @RequestBody Create input, Authentication auth) {
        long actor = Long.parseLong(auth.getName());
        family.requirePermission(actor, input.elderId(), PermissionColumn.MANAGE_REMINDER);
        validateRule(input.recurrence(), input.firstAt());
        reminders.create(input.elderId(), actor, input.title(), input.recurrence(), input.firstAt());
    }

    @PutMapping("/{id}")
    @Transactional
    public void update(@PathVariable long id, @Valid @RequestBody Update input, Authentication auth) {
        Map<String, Object> reminder = reminders.forUpdate(id);
        if (reminder == null) throw new ApiException(HttpStatus.NOT_FOUND, "REMINDER_NOT_FOUND", "提醒不存在");
        long elderId = ((Number) reminder.get("elder_id")).longValue();
        family.requirePermission(Long.parseLong(auth.getName()), elderId, PermissionColumn.MANAGE_REMINDER);
        if (!"ACTIVE".equals(reminder.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "REMINDER_STATE_CONFLICT", "提醒已停止或结束");
        }
        validateRule(input.recurrence(), input.nextFireAt());
        reminders.update(id, input.title(), input.recurrence(), input.nextFireAt());
    }

    private void validateRule(String recurrence, LocalDateTime firstAt) {
        if (!List.of("ONCE", "DAILY", "WEEKLY").contains(recurrence)
                || !firstAt.isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REMINDER_RULE_INVALID", "提醒规则或首次时间无效");
        }
    }

    @PostMapping("/{id}/stop")
    @Transactional
    public void stop(@PathVariable long id, Authentication auth) {
        Map<String, Object> reminder = reminders.forUpdate(id);
        if (reminder == null) throw new ApiException(HttpStatus.NOT_FOUND, "REMINDER_NOT_FOUND", "提醒不存在");
        long elderId = ((Number) reminder.get("elder_id")).longValue();
        family.requirePermission(Long.parseLong(auth.getName()), elderId, PermissionColumn.MANAGE_REMINDER);
        if (!"ACTIVE".equals(reminder.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "REMINDER_STATE_CONFLICT", "提醒已停止或结束");
        }
        reminders.stop(id);
    }

    /** 重新开启：只有被停止的提醒可以恢复，必须给出新的投递时间；已结束（一次性已投递）不可恢复。 */
    @PostMapping("/{id}/resume")
    @Transactional
    public void resume(@PathVariable long id, @Valid @RequestBody Resume input, Authentication auth) {
        Map<String, Object> reminder = reminders.forUpdate(id);
        if (reminder == null) throw new ApiException(HttpStatus.NOT_FOUND, "REMINDER_NOT_FOUND", "提醒不存在");
        long elderId = ((Number) reminder.get("elder_id")).longValue();
        family.requirePermission(Long.parseLong(auth.getName()), elderId, PermissionColumn.MANAGE_REMINDER);
        if (!"STOPPED".equals(reminder.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "REMINDER_STATE_CONFLICT", "只有已停止的提醒可以重新开启");
        }
        if (!input.nextFireAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REMINDER_RULE_INVALID", "重新开启的时间必须晚于当前时间");
        }
        reminders.resume(id, input.nextFireAt());
    }
}
