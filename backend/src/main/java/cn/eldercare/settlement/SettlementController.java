package cn.eldercare.settlement;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/settlement")
public class SettlementController {
    private final JdbcTemplate jdbc;
    private final FamilyService family;
    private final OrderActionAccess actionAccess;
    private final ObjectMapper json;
    private final String paymentUrl;
    private final String serviceToken;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public SettlementController(JdbcTemplate jdbc,FamilyService family,OrderActionAccess actionAccess,ObjectMapper json,
                                @Value("${app.payment-base-url}") String paymentUrl,
                                @Value("${app.payment-service-token:}") String serviceToken) {
        this.jdbc=jdbc;this.family=family;this.actionAccess=actionAccess;this.json=json;this.paymentUrl=paymentUrl;this.serviceToken=serviceToken;
    }
    public record View(Map<String,Object> settlement,List<Map<String,Object>> attempts) { }
    public record Attempt(String attemptRef,long orderRef,int settlementVersion,BigDecimal amount) { }
    private record Remote(String status,long orderRef,int settlementVersion,BigDecimal amount) { }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public View view(@PathVariable long orderId,Authentication auth) {
        requireRead(orderId,auth);
        Map<String,Object> settlement = settlement(orderId,false);
        List<Map<String,Object>> attempts=jdbc.queryForList("SELECT attempt_ref,amount,settlement_version,remote_status,created_at,synced_at FROM payment_attempt WHERE settlement_id=? ORDER BY id DESC",settlement.get("id"));
        return new View(settlement,attempts);
    }

    @PostMapping("/payment-attempts")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public Attempt attempt(@PathVariable long orderId,Authentication auth) {
        Map<String,Object> order=order(orderId,true);
        long actor=Long.parseLong(auth.getName());
        actionAccess.require(actor,order,"PAYMENT");
        Map<String,Object> settlement=settlement(orderId,true);
        String state=(String)settlement.get("status");
        if (!List.of("WAITING_METHOD","FAILED").contains(state)) throw conflict("SETTLEMENT_STATE_CONFLICT","当前不可创建支付尝试");
        long id=((Number)settlement.get("id")).longValue();
        String ref=UUID.randomUUID().toString();
        int version=((Number)settlement.get("version")).intValue();
        BigDecimal amount=(BigDecimal)settlement.get("amount");
        jdbc.update("INSERT INTO payment_attempt(settlement_id,attempt_ref,amount,settlement_version,remote_status) VALUES(?,?,?,?, 'UNREGISTERED')",id,ref,amount,version);
        jdbc.update("UPDATE settlement SET method='SIMULATED',status='PROCESSING' WHERE id=?",id);
        event(id,actor,"CREATE_ATTEMPT",ref);
        return new Attempt(ref,orderId,version,amount);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Transactional
    public View sync(@PathVariable long orderId,Authentication auth) {
        requireRead(orderId,auth);
        Map<String,Object> settlement=settlement(orderId,true);
        long id=((Number)settlement.get("id")).longValue();
        List<Map<String,Object>> attempts=jdbc.queryForList("SELECT * FROM payment_attempt WHERE settlement_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE",id);
        if(attempts.isEmpty())throw conflict("NO_ATTEMPT","尚未创建支付尝试");
        Map<String,Object> attempt=attempts.getFirst();
        String ref=(String)attempt.get("attempt_ref");
        Remote remote=query(ref);
        if(remote==null)return view(orderId,auth);
        if(remote.orderRef()!=orderId || remote.settlementVersion()!=((Number)attempt.get("settlement_version")).intValue()
                || remote.amount().compareTo((BigDecimal)attempt.get("amount"))!=0)
            throw conflict("REMOTE_MISMATCH","模拟交易的订单、版本或金额不匹配");
        if(!List.of("PROCESSING","SUCCESS","FAILED","CANCELLED").contains(remote.status()))
            throw conflict("REMOTE_STATE_INVALID","模拟交易状态无效");
        if(remote.status().equals(attempt.get("remote_status")) && attempt.get("synced_at")!=null)
            return view(orderId,auth);
        jdbc.update("UPDATE payment_attempt SET remote_status=?,synced_at=? WHERE id=?",remote.status(),LocalDateTime.now(),attempt.get("id"));
        if("SETTLED".equals(settlement.get("status")))return view(orderId,auth);
        if("SUCCESS".equals(remote.status())) {
            jdbc.update("UPDATE settlement SET status='SETTLED',method='SIMULATED',settled_at=? WHERE id=?",LocalDateTime.now(),id);
            event(id,Long.parseLong(auth.getName()),"SIMULATED_SETTLED",ref);
        } else if(List.of("FAILED","CANCELLED").contains(remote.status())) {
            jdbc.update("UPDATE settlement SET status='FAILED' WHERE id=?",id);
            event(id,Long.parseLong(auth.getName()),"SIMULATED_TERMINAL",remote.status());
        }
        return view(orderId,auth);
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public View offline(@PathVariable long orderId,Authentication auth) {
        Map<String,Object> settlement=settlement(orderId,true);
        long id=((Number)settlement.get("id")).longValue();
        if(!List.of("WAITING_METHOD","FAILED").contains(settlement.get("status")))throw conflict("SETTLEMENT_STATE_CONFLICT","当前不可登记线下结算");
        Integer unresolved=jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt WHERE settlement_id=? AND remote_status NOT IN ('FAILED','CANCELLED')",Integer.class,id);
        if(unresolved!=null&&unresolved>0)throw conflict("PAYMENT_ATTEMPT_UNRESOLVED","存在未终结的模拟交易，不能登记线下结算");
        jdbc.update("UPDATE settlement SET status='SETTLED',method='OFFLINE',settled_at=? WHERE id=?",LocalDateTime.now(),id);
        event(id,Long.parseLong(auth.getName()),"OFFLINE_SETTLED","管理员登记线下结算");
        return view(orderId,auth);
    }

    private Remote query(String ref) {
        if(serviceToken.isBlank())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_NOT_CONFIGURED","模拟支付查询令牌未配置");
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(paymentUrl+"/api/transactions/"+ref))
                    .timeout(Duration.ofSeconds(4)).header("X-Service-Token",serviceToken).GET().build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404)return null;
            if(response.statusCode()!=200)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_QUERY_UNAVAILABLE","模拟支付查询不可用");
            JsonNode body=json.readTree(response.body());
            return new Remote(body.path("status").asText(),body.path("orderRef").asLong(),body.path("settlementVersion").asInt(),new BigDecimal(body.path("amount").asText()));
        } catch(ApiException exception){throw exception;}
        catch(Exception exception){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_QUERY_UNAVAILABLE","模拟支付查询不可用");}
    }
    private void requireRead(long orderId,Authentication auth) {
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")))return;
        Map<String,Object> order=order(orderId,false);
        family.requirePermission(Long.parseLong(auth.getName()),((Number)order.get("elder_id")).longValue(),"can_view_service");
    }
    private Map<String,Object> order(long id,boolean lock){List<Map<String,Object>>rows=jdbc.queryForList("SELECT id,creator_id,elder_id FROM service_order WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","订单不存在");return rows.getFirst();}
    private Map<String,Object> settlement(long orderId,boolean lock){List<Map<String,Object>>rows=jdbc.queryForList("SELECT * FROM settlement WHERE order_id=?"+(lock?" FOR UPDATE":""),orderId);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"SETTLEMENT_NOT_FOUND","订单尚未进入结算");return rows.getFirst();}
    private void event(long settlementId,long actor,String action,String note){jdbc.update("INSERT INTO settlement_event(settlement_id,actor_id,action,note) VALUES(?,?,?,?)",settlementId,actor,action,note);}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
