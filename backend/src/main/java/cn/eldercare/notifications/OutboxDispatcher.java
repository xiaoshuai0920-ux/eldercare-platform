package cn.eldercare.notifications;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public OutboxDispatcher(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);}

    @Scheduled(fixedDelay=10000,initialDelay=10000)
    public void scan(){for(int i=0;i<20;i++){if(!Boolean.TRUE.equals(transaction.execute(status->deliverNext())))break;}}

    private boolean deliverNext(){
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,order_event_id,attempts FROM outbox_event WHERE status='PENDING' AND next_attempt_at<=? ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",now);
        if(rows.isEmpty())return false;
        Map<String,Object> job=rows.getFirst();
        long id=((Number)job.get("id")).longValue();
        try{
            Map<String,Object> event=jdbc.queryForMap("SELECT e.id,e.order_id,e.action,e.new_status,e.note,o.elder_id,o.item_name_snapshot FROM order_event e JOIN service_order o ON o.id=e.order_id WHERE e.id=?",job.get("order_event_id"));
            long orderId=((Number)event.get("order_id")).longValue();
            long elderId=((Number)event.get("elder_id")).longValue();
            String title="订单 #"+orderId+" · "+event.get("item_name_snapshot");
            String body=event.get("note")==null?"订单状态已更新":String.valueOf(event.get("note"));
            String state=String.valueOf(event.get("new_status"));
            String userPriority="WAITING_CONFIRMATION".equals(state)?"ACTION":"NORMAL";
            for(Map<String,Object> recipient:jdbc.queryForList("SELECT DISTINCT a.id FROM family_grant g JOIN account a ON a.id=g.account_id WHERE g.elder_id=? AND g.status='ACTIVE' AND g.can_view_service=1 AND a.status='ACTIVE'",elderId))
                insert(((Number)recipient.get("id")).longValue(),((Number)event.get("id")).longValue(),title,body,"/user/orders",userPriority);
            String workerPriority="WAITING_ACCEPTANCE".equals(state)?"ACTION":"NORMAL";
            for(Map<String,Object> recipient:jdbc.queryForList("SELECT DISTINCT a.id FROM assignment x JOIN account a ON a.id=x.worker_id WHERE x.order_id=? AND x.status IN ('PENDING','ACCEPTED') AND a.status='ACTIVE'",orderId))
                insert(((Number)recipient.get("id")).longValue(),((Number)event.get("id")).longValue(),title,body,"/worker",workerPriority);
            String adminPriority="INTERRUPTED".equals(state)?"HIGH":"WAITING_ASSIGNMENT".equals(state)?"ACTION":"NORMAL";
            for(Map<String,Object> recipient:jdbc.queryForList("SELECT id FROM account WHERE role='ADMIN' AND status='ACTIVE'"))
                insert(((Number)recipient.get("id")).longValue(),((Number)event.get("id")).longValue(),title,body,"/admin/operations",adminPriority);
            jdbc.update("UPDATE outbox_event SET status='DONE',processed_at=?,last_error=NULL WHERE id=?",now,id);
        }catch(RuntimeException error){
            int attempts=((Number)job.get("attempts")).intValue()+1;
            jdbc.update("UPDATE outbox_event SET attempts=?,status=?,next_attempt_at=?,last_error=? WHERE id=?",attempts,attempts>=5?"FAILED":"PENDING",now.plusSeconds(Math.min(300,attempts*30L)),error.getClass().getSimpleName(),id);
        }
        return true;
    }

    private void insert(long accountId,long eventId,String title,String body,String path,String priority){
        jdbc.update("INSERT IGNORE INTO notification(account_id,order_event_id,title,body,target_path,priority) VALUES(?,?,?,?,?,?)",accountId,eventId,title,body,path,priority);
    }
}
