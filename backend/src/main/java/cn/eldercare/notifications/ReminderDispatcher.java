package cn.eldercare.notifications;

import cn.eldercare.common.SqlValues;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ReminderDispatcher {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public ReminderDispatcher(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);}

    @Scheduled(fixedDelay=60000,initialDelay=15000)
    public void scan(){for(int i=0;i<30;i++){if(!Boolean.TRUE.equals(transaction.execute(status->deliverNext())))break;}}

    private boolean deliverNext(){
        LocalDateTime now=LocalDateTime.now(ZONE);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM life_reminder WHERE status='ACTIVE' AND next_fire_at<=? ORDER BY next_fire_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",now);
        if(rows.isEmpty())return false;
        Map<String,Object> reminder=rows.getFirst();
        long id=((Number)reminder.get("id")).longValue();
        LocalDateTime original=SqlValues.localDateTime(reminder.get("next_fire_at"));
        String recurrence=(String)reminder.get("recurrence");
        LocalDateTime due=original;
        LocalDateTime next=null;
        if("DAILY".equals(recurrence)){
            long periods=Math.max(0,ChronoUnit.DAYS.between(original,now));
            due=original.plusDays(periods);
            if(due.isAfter(now))due=due.minusDays(1);
            next=due.plusDays(1);
        }else if("WEEKLY".equals(recurrence)){
            long periods=Math.max(0,ChronoUnit.DAYS.between(original,now)/7);
            due=original.plusWeeks(periods);
            if(due.isAfter(now))due=due.minusWeeks(1);
            next=due.plusWeeks(1);
        }
        if(!due.equals(original))jdbc.update("INSERT INTO reminder_delivery(reminder_id,due_at,account_id,status) VALUES(?,?,NULL,'EXPIRED')",id,original);
        if(Duration.between(due,now).compareTo(Duration.ofHours(24))>0){
            jdbc.update("INSERT INTO reminder_delivery(reminder_id,due_at,account_id,status) VALUES(?,?,NULL,'EXPIRED')",id,due);
        }else{
            List<Map<String,Object>> recipients=jdbc.queryForList("SELECT a.id FROM family_grant g JOIN account a ON a.id=g.account_id WHERE g.elder_id=? AND g.status='ACTIVE' AND g.can_manage_reminder=1 AND a.status='ACTIVE'",reminder.get("elder_id"));
            if(recipients.isEmpty())jdbc.update("INSERT INTO reminder_delivery(reminder_id,due_at,account_id,status) VALUES(?,?,NULL,'NO_RECIPIENT')",id,due);
            for(Map<String,Object> recipient:recipients){
                long account=((Number)recipient.get("id")).longValue();
                jdbc.update("INSERT IGNORE INTO reminder_delivery(reminder_id,due_at,account_id,status) VALUES(?,?,?,'DELIVERED')",id,due,account);
                jdbc.update("INSERT IGNORE INTO notification(account_id,reminder_id,reminder_due_at,title,body,target_path,priority) VALUES(?,?,?,?,?,'/user/reminders','NORMAL')",account,id,due,"生活提醒",reminder.get("title"));
            }
        }
        if(next==null)jdbc.update("UPDATE life_reminder SET status='FINISHED',next_fire_at=NULL WHERE id=?",id);
        else jdbc.update("UPDATE life_reminder SET next_fire_at=? WHERE id=?",next,id);
        return true;
    }
}
