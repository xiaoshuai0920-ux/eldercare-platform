package cn.eldercare.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
@RequestMapping("/api")
public class IncidentController {
    private final JdbcTemplate jdbc;
    private final FamilyService family;
    private final OrderActionAccess actionAccess;
    public IncidentController(JdbcTemplate jdbc,FamilyService family,OrderActionAccess actionAccess){this.jdbc=jdbc;this.family=family;this.actionAccess=actionAccess;}
    public record Reason(@NotBlank @Size(max=500) String reason){}
    public record FeeProposal(@NotNull @DecimalMin("0.00") BigDecimal amount,@NotBlank @Size(max=500) String reason){}
    public record SupplementRequest(@NotNull LocalDateTime startAt,@NotBlank @Size(max=500) String reason){}

    @PostMapping("/orders/{orderId}/interrupt")
    @PreAuthorize("hasRole('WORKER')")
    @Transactional
    public void interrupt(@PathVariable long orderId,@Valid @RequestBody Reason input,Authentication auth){
        interruptInternal(orderId,Long.parseLong(auth.getName()),input.reason(),false);
    }

    @PostMapping("/admin/orders/{orderId}/interrupt")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void adminInterrupt(@PathVariable long orderId,@Valid @RequestBody Reason input,Authentication auth){
        interruptInternal(orderId,Long.parseLong(auth.getName()),input.reason(),true);
    }

    private void interruptInternal(long orderId,long actor,String reason,boolean admin){
        Map<String,Object> order=order(orderId,true);
        if(!"IN_SERVICE".equals(order.get("status")))throw conflict("INTERRUPT_STATE_CONFLICT","只有服务中订单可报告中断");
        List<Map<String,Object>> assignments=admin
                ? jdbc.queryForList("SELECT id FROM assignment WHERE order_id=? AND status='ACCEPTED' FOR UPDATE",orderId)
                : jdbc.queryForList("SELECT id FROM assignment WHERE order_id=? AND worker_id=? AND status='ACCEPTED' FOR UPDATE",orderId,actor);
        if(assignments.isEmpty())throw new ApiException(HttpStatus.FORBIDDEN,"ASSIGNMENT_FORBIDDEN","当前没有有效的本人派单");
        long assignmentId=((Number)assignments.getFirst().get("id")).longValue();
        jdbc.update("INSERT INTO service_incident(order_id,reported_by,reason,status) VALUES(?,?,?,'OPEN')",orderId,actor,reason.trim());
        jdbc.update("UPDATE fulfillment SET status='INTERRUPTED' WHERE order_id=? AND assignment_id=? AND status='IN_PROGRESS'",orderId,assignmentId);
        jdbc.update("UPDATE worker_occupancy SET active=0 WHERE assignment_id=?",assignmentId);
        jdbc.update("UPDATE assignment SET status='RELEASED' WHERE id=?",assignmentId);
        jdbc.update("UPDATE supplement_plan SET status='INTERRUPTED' WHERE order_id=? AND status='IN_SERVICE'",orderId);
        transition(order,"INTERRUPTED");
        event(orderId,actor,"INTERRUPT","IN_SERVICE","INTERRUPTED",reason.trim());
    }

    @GetMapping("/admin/incidents")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> incidents(){
        return jdbc.queryForList("SELECT i.id,i.order_id,i.reason,i.status,i.created_at,o.status AS order_status,o.item_name_snapshot,o.payable_amount,e.full_name AS elder_name FROM service_incident i JOIN service_order o ON o.id=i.order_id JOIN elder_profile e ON e.id=o.elder_id ORDER BY i.id DESC LIMIT 100");
    }

    @PostMapping("/admin/orders/{orderId}/supplements")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void scheduleSupplement(@PathVariable long orderId,@Valid @RequestBody SupplementRequest input,Authentication auth){
        Map<String,Object> order=order(orderId,true);
        if(!"INTERRUPTED".equals(order.get("status")))throw conflict("SUPPLEMENT_STATE_CONFLICT","只有中断订单可安排补服务");
        int duration=((Number)order.get("duration_minutes")).intValue();
        LocalDateTime endAt=input.startAt().plusMinutes(duration);
        if(!input.startAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))) || !input.startAt().toLocalDate().equals(endAt.toLocalDate()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"SUPPLEMENT_TIME_INVALID","补服务须安排在未来且不能跨日");
        jdbc.update("INSERT INTO supplement_plan(order_id,created_by,reason,start_at,end_at,status) VALUES(?,?,?,?,?,'PLANNED')",orderId,Long.parseLong(auth.getName()),input.reason().trim(),input.startAt(),endAt);
        transition(order,"WAITING_ASSIGNMENT");
        event(orderId,Long.parseLong(auth.getName()),"PLAN_SUPPLEMENT","INTERRUPTED","WAITING_ASSIGNMENT","已安排独立补服务，等待人工派单");
    }

    @GetMapping("/orders/{orderId}/supplements")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<Map<String,Object>> supplements(@PathVariable long orderId,Authentication auth){
        Map<String,Object> order=order(orderId,false);
        if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            family.requirePermission(Long.parseLong(auth.getName()),((Number)order.get("elder_id")).longValue(),"can_view_service");
        return jdbc.queryForList("SELECT id,reason,start_at,end_at,status,created_at FROM supplement_plan WHERE order_id=? ORDER BY id DESC",orderId);
    }

    @PostMapping("/orders/{orderId}/fee-adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void proposeFee(@PathVariable long orderId,@Valid @RequestBody FeeProposal input,Authentication auth){
        Map<String,Object> order=order(orderId,true);
        if(!"INTERRUPTED".equals(order.get("status")))throw conflict("FEE_STATE_CONFLICT","仅中断订单可按此流程核实费用");
        Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM fee_adjustment WHERE order_id=? AND status='PENDING'",Integer.class,orderId);
        if(pending!=null&&pending>0)throw conflict("FEE_PROPOSAL_PENDING","已有待确认费用提案");
        jdbc.update("INSERT INTO fee_adjustment(order_id,proposed_by,original_amount,proposed_amount,reason,status) VALUES(?,?,?,?,?,'PENDING')",orderId,Long.parseLong(auth.getName()),order.get("payable_amount"),input.amount(),input.reason().trim());
        event(orderId,Long.parseLong(auth.getName()),"PROPOSE_FEE","INTERRUPTED","INTERRUPTED","管理员提出异常费用，待有权用户确认");
    }

    @GetMapping("/orders/{orderId}/fee-adjustments")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<Map<String,Object>> fees(@PathVariable long orderId,Authentication auth){
        Map<String,Object> order=order(orderId,false);
        if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            family.requirePermission(Long.parseLong(auth.getName()),((Number)order.get("elder_id")).longValue(),"can_view_service");
        return jdbc.queryForList("SELECT id,original_amount,proposed_amount,reason,status,created_at FROM fee_adjustment WHERE order_id=? ORDER BY id DESC",orderId);
    }

    @PostMapping("/orders/{orderId}/fee-adjustments/{id}/approve")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void approveFee(@PathVariable long orderId,@PathVariable long id,Authentication auth){decideFee(orderId,id,auth,true);}

    @PostMapping("/orders/{orderId}/fee-adjustments/{id}/reject")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void rejectFee(@PathVariable long orderId,@PathVariable long id,Authentication auth){decideFee(orderId,id,auth,false);}

    private void decideFee(long orderId,long id,Authentication auth,boolean approve){
        Map<String,Object> order=order(orderId,true);
        long actor=Long.parseLong(auth.getName());
        actionAccess.require(actor,order,"FEE_CONFIRM");
        if(!"INTERRUPTED".equals(order.get("status")))throw conflict("FEE_STATE_CONFLICT","订单不在异常费用待核实阶段");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM fee_adjustment WHERE id=? AND order_id=? FOR UPDATE",id,orderId);
        if(rows.isEmpty()||!"PENDING".equals(rows.getFirst().get("status")))throw conflict("FEE_PROPOSAL_STATE_CONFLICT","费用提案不存在或已处理");
        Map<String,Object> fee=rows.getFirst();
        jdbc.update("UPDATE fee_adjustment SET status=?,decided_by=?,decided_at=? WHERE id=?",approve?"APPROVED":"REJECTED",actor,LocalDateTime.now(),id);
        if(!approve){event(orderId,actor,"REJECT_FEE","INTERRUPTED","INTERRUPTED","用户拒绝费用提案，继续异常协调");return;}
        BigDecimal amount=(BigDecimal)fee.get("proposed_amount");
        jdbc.update("UPDATE service_order SET payable_amount=? WHERE id=?",amount,orderId);
        transition(order,"ABNORMAL_CLOSED");
        jdbc.update("UPDATE service_incident SET status='RESOLVED',resolved_by=?,resolution=?,resolved_at=? WHERE order_id=? AND status='OPEN'",fee.get("proposed_by"),"用户已确认异常结案费用",LocalDateTime.now(),orderId);
        if(amount.signum()>0)jdbc.update("INSERT INTO settlement(order_id,amount,version,status) VALUES(?,?,1,'WAITING_METHOD')",orderId,amount);
        else jdbc.update("INSERT INTO settlement(order_id,amount,version,method,status,settled_at) VALUES(?,0,1,'WAIVED','SETTLED',?)",orderId,LocalDateTime.now());
        event(orderId,actor,"APPROVE_FEE","INTERRUPTED","ABNORMAL_CLOSED","有权用户确认异常结案费用");
    }

    @PostMapping("/orders/{orderId}/disputes")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void dispute(@PathVariable long orderId,@Valid @RequestBody Reason input,Authentication auth){
        Map<String,Object> order=order(orderId,true);
        long actor=Long.parseLong(auth.getName());
        actionAccess.require(actor,order,"DISPUTE");
        if(!"COMPLETED".equals(order.get("status")))throw conflict("DISPUTE_STATE_CONFLICT","仅正常完成的订单可发起事后争议");
        Integer open=jdbc.queryForObject("SELECT COUNT(*) FROM order_dispute WHERE order_id=? AND status='OPEN'",Integer.class,orderId);
        if(open!=null&&open>0)throw conflict("DISPUTE_EXISTS","已有待处理争议");
        jdbc.update("INSERT INTO order_dispute(order_id,opened_by,reason,status) VALUES(?,?,?,'OPEN')",orderId,actor,input.reason().trim());
        event(orderId,actor,"OPEN_DISPUTE","COMPLETED","COMPLETED","完成后争议已登记");
    }

    @GetMapping("/admin/disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> disputes(){return jdbc.queryForList("SELECT d.id,d.order_id,d.reason,d.status,d.created_at,a.display_name AS opened_by_name FROM order_dispute d JOIN account a ON a.id=d.opened_by ORDER BY d.id DESC LIMIT 100");}

    @PostMapping("/admin/disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resolve(@PathVariable long id,@Valid @RequestBody Reason input,Authentication auth){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM order_dispute WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()||!"OPEN".equals(rows.getFirst().get("status")))throw conflict("DISPUTE_STATE_CONFLICT","争议不存在或已处理");
        jdbc.update("UPDATE order_dispute SET status='RESOLVED',resolved_by=?,resolution=?,resolved_at=? WHERE id=?",Long.parseLong(auth.getName()),input.reason().trim(),LocalDateTime.now(),id);
    }

    private Map<String,Object> order(long id,boolean locked){List<Map<String,Object>>rows=jdbc.queryForList("SELECT id,creator_id,elder_id,item_id,duration_minutes,status,version,payable_amount FROM service_order WHERE id=?"+(locked?" FOR UPDATE":""),id);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");return rows.getFirst();}
    private void transition(Map<String,Object> order,String next){int changed=jdbc.update("UPDATE service_order SET status=?,version=version+1 WHERE id=? AND version=? AND status=?",next,order.get("id"),order.get("version"),order.get("status"));if(changed!=1)throw conflict("ORDER_STATE_CONFLICT","订单状态已变化");}
    private void event(long orderId,long actor,String action,String from,String to,String note){jdbc.update("INSERT INTO order_event(order_id,actor_id,action,old_status,new_status,note) VALUES(?,?,?,?,?,?)",orderId,actor,action,from,to,note);jdbc.update("INSERT INTO outbox_event(order_event_id) VALUES(LAST_INSERT_ID())");}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
