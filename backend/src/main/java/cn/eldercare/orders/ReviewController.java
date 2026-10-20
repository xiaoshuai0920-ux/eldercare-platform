package cn.eldercare.orders;

import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
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
@RequestMapping("/api")
public class ReviewController {
    private final JdbcTemplate jdbc;
    private final FamilyService family;
    private final OrderActionAccess actionAccess;
    public ReviewController(JdbcTemplate jdbc,FamilyService family,OrderActionAccess actionAccess){this.jdbc=jdbc;this.family=family;this.actionAccess=actionAccess;}
    public record ReviewRequest(@Min(1) @Max(5) int stars,@Size(max=1000) String content){}
    public record ModerateRequest(@NotBlank @Size(max=300) String reason){}

    @GetMapping("/orders/{orderId}/review")
    @PreAuthorize("hasRole('USER')")
    public Map<String,Object> review(@PathVariable long orderId,Authentication auth){
        Map<String,Object> order=order(orderId,false);
        family.requirePermission(Long.parseLong(auth.getName()),((Number)order.get("elder_id")).longValue(),"can_view_service");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,stars,content,visibility,created_at FROM service_review WHERE order_id=?",orderId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"REVIEW_NOT_FOUND","尚未评价");
        return rows.getFirst();
    }

    @PostMapping("/orders/{orderId}/review")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void create(@PathVariable long orderId,@Valid @RequestBody ReviewRequest input,Authentication auth){
        Map<String,Object> order=order(orderId,true);
        long actor=Long.parseLong(auth.getName());
        actionAccess.require(actor,order,"REVIEW");
        if(!"COMPLETED".equals(order.get("status")))throw new ApiException(HttpStatus.CONFLICT,"REVIEW_STATE_CONFLICT","仅正常确认完成的订单可以评价");
        try{jdbc.update("INSERT INTO service_review(order_id,author_id,stars,content) VALUES(?,?,?,?)",orderId,actor,input.stars(),input.content()==null?null:input.content().trim());}
        catch(DuplicateKeyException duplicate){throw new ApiException(HttpStatus.CONFLICT,"REVIEW_EXISTS","该订单已评价");}
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> adminReviews(){
        return jdbc.queryForList("SELECT r.id,r.order_id,r.stars,r.content,r.visibility,r.created_at,a.display_name AS author_name FROM service_review r JOIN account a ON a.id=r.author_id ORDER BY r.id DESC LIMIT 100");
    }

    @PostMapping("/admin/reviews/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void hide(@PathVariable long id,@Valid @RequestBody ModerateRequest input,Authentication auth){
        int changed=jdbc.update("UPDATE service_review SET visibility='HIDDEN',moderated_by=?,moderation_reason=?,moderated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND visibility='VISIBLE'",Long.parseLong(auth.getName()),input.reason().trim(),id);
        if(changed!=1)throw new ApiException(HttpStatus.CONFLICT,"REVIEW_STATE_CONFLICT","评价不存在或已隐藏");
    }

    private Map<String,Object> order(long id,boolean lock){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,creator_id,elder_id,status FROM service_order WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");return rows.getFirst();}
}
