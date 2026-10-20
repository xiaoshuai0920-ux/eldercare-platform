package cn.eldercare.notifications;

import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    public NotificationController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping
    public List<Map<String,Object>> list(Authentication auth,@RequestParam(defaultValue="50") int size){
        if(size<1||size>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE_SIZE","每页数量须为 1–100");
        return jdbc.queryForList("SELECT n.id,n.title,n.body,n.target_path,n.priority,n.read_at,n.created_at FROM notification n WHERE n.account_id=? AND "+visible(auth)+" ORDER BY n.id DESC LIMIT ?",Long.parseLong(auth.getName()),size);
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable long id,Authentication auth){
        long actor=Long.parseLong(auth.getName());
        Integer visible=jdbc.queryForObject("SELECT COUNT(*) FROM notification n WHERE n.id=? AND n.account_id=? AND "+visible(auth),Integer.class,id,actor);
        if(visible==null||visible==0)throw new ApiException(HttpStatus.NOT_FOUND,"NOTIFICATION_NOT_FOUND","消息不存在或当前无权查看");
        jdbc.update("UPDATE notification SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) WHERE id=? AND account_id=?",id,actor);
    }

    private String visible(Authentication auth){
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")))return "1=1";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_WORKER")))
            return "n.order_event_id IS NOT NULL AND EXISTS(SELECT 1 FROM order_event e JOIN assignment x ON x.order_id=e.order_id WHERE e.id=n.order_event_id AND x.worker_id=n.account_id AND x.status IN ('PENDING','ACCEPTED'))";
        return "((n.order_event_id IS NOT NULL AND EXISTS(SELECT 1 FROM order_event e JOIN service_order o ON o.id=e.order_id JOIN family_grant g ON g.elder_id=o.elder_id WHERE e.id=n.order_event_id AND g.account_id=n.account_id AND g.status='ACTIVE' AND g.can_view_service=1)) OR (n.reminder_id IS NOT NULL AND EXISTS(SELECT 1 FROM life_reminder r JOIN family_grant g ON g.elder_id=r.elder_id WHERE r.id=n.reminder_id AND g.account_id=n.account_id AND g.status='ACTIVE' AND g.can_manage_reminder=1)))";
    }
}
