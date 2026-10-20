package cn.eldercare.notifications;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/reminders")
@PreAuthorize("hasRole('USER')")
public class ReminderController {
    private final JdbcTemplate jdbc;
    private final FamilyService family;
    public ReminderController(JdbcTemplate jdbc,FamilyService family){this.jdbc=jdbc;this.family=family;}
    public record Create(@NotNull Long elderId,@NotBlank @Size(max=100) String title,@NotBlank String recurrence,@NotNull LocalDateTime firstAt){}

    @GetMapping
    public List<Map<String,Object>> list(Authentication auth){
        return jdbc.queryForList("SELECT r.id,r.elder_id,e.full_name AS elder_name,r.title,r.recurrence,r.next_fire_at,r.status FROM life_reminder r JOIN elder_profile e ON e.id=r.elder_id JOIN family_grant g ON g.elder_id=r.elder_id WHERE g.account_id=? AND g.status='ACTIVE' AND g.can_manage_reminder=1 ORDER BY r.id DESC LIMIT 100",Long.parseLong(auth.getName()));
    }

    @PostMapping
    @Transactional
    public void create(@Valid @RequestBody Create input,Authentication auth){
        long actor=Long.parseLong(auth.getName());
        family.requirePermission(actor,input.elderId(),"can_manage_reminder");
        if(!List.of("ONCE","DAILY","WEEKLY").contains(input.recurrence())||!input.firstAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw new ApiException(HttpStatus.BAD_REQUEST,"REMINDER_RULE_INVALID","提醒规则或首次时间无效");
        jdbc.update("INSERT INTO life_reminder(elder_id,created_by,title,recurrence,next_fire_at,status) VALUES(?,?,?,?,?,'ACTIVE')",input.elderId(),actor,input.title().trim(),input.recurrence(),input.firstAt());
    }

    @PostMapping("/{id}/stop")
    @Transactional
    public void stop(@PathVariable long id,Authentication auth){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT elder_id,status FROM life_reminder WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"REMINDER_NOT_FOUND","提醒不存在");
        family.requirePermission(Long.parseLong(auth.getName()),((Number)rows.getFirst().get("elder_id")).longValue(),"can_manage_reminder");
        if(!"ACTIVE".equals(rows.getFirst().get("status")))throw new ApiException(HttpStatus.CONFLICT,"REMINDER_STATE_CONFLICT","提醒已停止或结束");
        jdbc.update("UPDATE life_reminder SET status='STOPPED',next_fire_at=NULL WHERE id=?",id);
    }
}
