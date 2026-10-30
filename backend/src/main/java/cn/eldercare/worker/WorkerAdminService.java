package cn.eldercare.worker;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.AuditService;
import cn.eldercare.common.PageQuery;
import cn.eldercare.common.PageResult;
import cn.eldercare.identity.AccountEntity;
import cn.eldercare.identity.AccountMapper;
import cn.eldercare.identity.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerAdminService {
    private final JdbcTemplate jdbc;
    private final AccountMapper accounts;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public WorkerAdminService(JdbcTemplate jdbc, AccountMapper accounts, PasswordEncoder encoder, AuditService audit) {
        this.jdbc = jdbc; this.accounts = accounts; this.encoder = encoder; this.audit = audit;
    }

    @Transactional
    public long createWorker(long adminId, String inputUsername, String name, String password, String rawEmail) {
        String username = AuthService.normalizeUsername(inputUsername);
        AuthService.validateUsername(username);
        AuthService.validatePassword(password);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE username=?", Integer.class, username);
        if (exists != null && exists > 0) throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该账号已被注册");
        AccountEntity worker = new AccountEntity();
        worker.setUsername(username);
        worker.setEmail(AuthService.normalizeEmail(rawEmail));
        worker.setDisplayName(name == null || name.isBlank() ? username : name.trim());
        worker.setPasswordHash(encoder.encode(password));
        worker.setRole("WORKER");
        worker.setStatus("ACTIVE");
        accounts.insert(worker);
        jdbc.update("UPDATE account SET accepting=1 WHERE id=?", worker.getId());
        audit.record(adminId, "WORKER_CREATE", "ACCOUNT", worker.getId(), "创建服务人员账号");
        return worker.getId();
    }

    public PageResult<Map<String, Object>> workers(int page, int size) {
        PageQuery query = PageQuery.of(page, size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE role='WORKER'", Long.class);
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id,username,email,display_name,status,accepting FROM account WHERE role='WORKER' ORDER BY id DESC LIMIT ? OFFSET ?", query.size(), query.offset());
        return PageResult.of(items, query, total == null ? 0 : total);
    }

    @Transactional
    public void capability(long adminId, long workerId, long itemId, long streetId) {
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM service_item_area WHERE item_id=? AND street_id=?", Integer.class, itemId, streetId);
        if (valid == null || valid == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "CAPABILITY_SCOPE_INVALID", "项目未覆盖该街道");
        jdbc.update("INSERT INTO worker_capability(worker_id,item_id,street_id,active) VALUES(?,?,?,1) ON DUPLICATE KEY UPDATE active=1", workerId, itemId, streetId);
        audit.record(adminId, "WORKER_CAPABILITY_GRANT", "ACCOUNT", workerId, "授予服务项目 #" + itemId + " 在街道 #" + streetId + " 的接单能力");
    }

    public List<Map<String, Object>> capabilities(long workerId) {
        return jdbc.queryForList("SELECT c.item_id,c.street_id,c.active,i.name AS item_name,a.name AS street_name FROM worker_capability c JOIN service_item i ON i.id=c.item_id JOIN service_area a ON a.id=c.street_id WHERE c.worker_id=? ORDER BY c.item_id,c.street_id",workerId);
    }

    public List<Map<String, Object>> schedules(long workerId) {
        return jdbc.queryForList("SELECT id,weekday,start_time,end_time FROM worker_schedule WHERE worker_id=? ORDER BY weekday,start_time",workerId);
    }

    @Transactional
    public void schedule(long adminId, long workerId, int weekday, LocalTime start, LocalTime end) {
        if (weekday < 1 || weekday > 7 || !start.isBefore(end)) throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULE_INVALID", "工作时段无效");
        lockWorkerForSchedule(workerId);
        Integer overlap = jdbc.queryForObject("SELECT COUNT(*) FROM worker_schedule WHERE worker_id=? AND weekday=? AND start_time<? AND end_time>?", Integer.class, workerId, weekday, end, start);
        if (overlap != null && overlap > 0) throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_OVERLAP", "工作时段重叠");
        jdbc.update("INSERT INTO worker_schedule(worker_id,weekday,start_time,end_time) VALUES(?,?,?,?)", workerId, weekday, start, end);
        audit.record(adminId, "WORKER_SCHEDULE_ADD", "ACCOUNT", workerId, "新增排班：周" + weekday + " " + start + "-" + end);
    }

    @Transactional
    public void updateSchedule(long adminId, long workerId, long scheduleId, int weekday, LocalTime start, LocalTime end) {
        if (weekday < 1 || weekday > 7 || !start.isBefore(end)) throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULE_INVALID", "工作时段无效");
        lockWorkerForSchedule(workerId);
        List<Map<String,Object>> current = jdbc.queryForList(
                "SELECT id FROM worker_schedule WHERE worker_id=? AND id=? FOR UPDATE", workerId, scheduleId);
        if (current.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "排班不存在");
        Integer overlap = jdbc.queryForObject(
                "SELECT COUNT(*) FROM worker_schedule WHERE worker_id=? AND weekday=? AND start_time<? AND end_time>? AND id<>?",
                Integer.class, workerId, weekday, end, start, scheduleId);
        if (overlap != null && overlap > 0) throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_OVERLAP", "工作时段重叠");
        jdbc.update("UPDATE worker_schedule SET weekday=?,start_time=?,end_time=? WHERE worker_id=? AND id=?",
                weekday, start, end, workerId, scheduleId);
        audit.record(adminId, "WORKER_SCHEDULE_UPDATE", "ACCOUNT", workerId,
                "修改排班 #" + scheduleId + "：周" + weekday + " " + start + "-" + end);
    }

    @Transactional
    public void deleteSchedule(long adminId, long workerId, long scheduleId) {
        lockWorkerForSchedule(workerId);
        int deleted = jdbc.update("DELETE FROM worker_schedule WHERE worker_id=? AND id=?", workerId, scheduleId);
        if (deleted == 0) throw new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "排班不存在");
        audit.record(adminId, "WORKER_SCHEDULE_DELETE", "ACCOUNT", workerId, "删除排班 #" + scheduleId);
    }

    private void lockWorkerForSchedule(long workerId) {
        List<Map<String,Object>> workers = jdbc.queryForList(
                "SELECT id FROM account WHERE id=? AND role='WORKER' FOR UPDATE", workerId);
        if (workers.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "WORKER_NOT_FOUND", "服务人员不存在");
    }

    public void toggleAccepting(long workerId, boolean accepting) {
        int changed = jdbc.update("UPDATE account SET accepting=? WHERE id=? AND role='WORKER' AND status='ACTIVE'", accepting, workerId);
        if (changed == 1) {
            audit.record(workerId, "WORKER_ACCEPTING_TOGGLE", "ACCOUNT", workerId, accepting ? "开启接单" : "关闭接单");
        }
    }

    @Transactional
    public void adminWorkerState(long adminId,long workerId,String action,String reason){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,accepting FROM account WHERE id=? AND role='WORKER' FOR UPDATE",workerId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"WORKER_NOT_FOUND","服务人员不存在");
        String status=(String)rows.getFirst().get("status");
        Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM assignment WHERE worker_id=? AND status IN ('PENDING','ACCEPTED')",Integer.class,workerId);
        if("PAUSE".equals(action)){
            if(!"ACTIVE".equals(status))throw new ApiException(HttpStatus.CONFLICT,"WORKER_STATE_CONFLICT","账号未激活");
            jdbc.update("UPDATE account SET accepting=0 WHERE id=?",workerId);
        }else if("DISABLE".equals(action)){
            if(!"ACTIVE".equals(status)||active!=null&&active>0)throw new ApiException(HttpStatus.CONFLICT,"WORKER_ACTIVE_TASKS","普通停用前请先协调全部有效派单");
            jdbc.update("UPDATE account SET status='DISABLED',accepting=0 WHERE id=?",workerId);
        }else if("EMERGENCY_DISABLE".equals(action)){
            if(!"ACTIVE".equals(status))throw new ApiException(HttpStatus.CONFLICT,"WORKER_STATE_CONFLICT","账号不是有效状态");
            jdbc.update("UPDATE account SET status='DISABLED',accepting=0 WHERE id=?",workerId);
        }else if("ENABLE".equals(action)){
            if(!"DISABLED".equals(status))throw new ApiException(HttpStatus.CONFLICT,"WORKER_STATE_CONFLICT","账号未停用");
            jdbc.update("UPDATE account SET status='ACTIVE',accepting=0 WHERE id=?",workerId);
        }else throw new ApiException(HttpStatus.BAD_REQUEST,"WORKER_ACTION_INVALID","人员操作无效");
        audit.record(adminId, action, "ACCOUNT", workerId, reason.trim());
    }

    @Transactional
    public void requestLeave(long workerId,LocalDateTime start,LocalDateTime end,String reason){
        if(!start.isBefore(end)||!start.isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw new ApiException(HttpStatus.BAD_REQUEST,"LEAVE_TIME_INVALID","请假时间须为将来的有效时段");
        jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='WORKER' AND status='ACTIVE' FOR UPDATE",workerId);
        Integer overlap=jdbc.queryForObject("SELECT COUNT(*) FROM worker_leave WHERE worker_id=? AND status IN ('PENDING','APPROVED') AND start_at<? AND end_at>?",Integer.class,workerId,end,start);
        if(overlap!=null&&overlap>0)throw new ApiException(HttpStatus.CONFLICT,"LEAVE_OVERLAP","请假时间与已有请假重叠");
        jdbc.update("INSERT INTO worker_leave(worker_id,start_at,end_at,reason,status) VALUES(?,?,?,?,'PENDING')",workerId,start,end,reason.trim());
        audit.record(workerId, "WORKER_LEAVE_REQUEST", "WORKER_LEAVE", workerId, "提交请假：" + start + " 至 " + end + "；" + reason.trim());
    }

    public List<Map<String,Object>> workerLeaves(long workerId){
        return jdbc.queryForList("SELECT id,start_at,end_at,reason,status,decided_at FROM worker_leave WHERE worker_id=? ORDER BY id DESC",workerId);
    }

    public List<Map<String,Object>> adminLeaves(){
        return jdbc.queryForList("SELECT l.id,l.worker_id,a.display_name AS worker_name,l.start_at,l.end_at,l.reason,l.status FROM worker_leave l JOIN account a ON a.id=l.worker_id ORDER BY l.id DESC LIMIT 100");
    }

    @Transactional
    public void decideLeave(long adminId,long leaveId,boolean approve){
        List<Map<String,Object>> candidate=jdbc.queryForList("SELECT worker_id FROM worker_leave WHERE id=?",leaveId);
        if(candidate.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"LEAVE_NOT_FOUND","请假申请不存在");
        long workerId=((Number)candidate.getFirst().get("worker_id")).longValue();
        jdbc.queryForList("SELECT id FROM account WHERE id=? FOR UPDATE",workerId);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM worker_leave WHERE id=? FOR UPDATE",leaveId);
        Map<String,Object> leave=rows.getFirst();
        if(!"PENDING".equals(leave.get("status")))throw new ApiException(HttpStatus.CONFLICT,"LEAVE_STATE_CONFLICT","请假申请已处理");
        if(approve){
            Integer busy=jdbc.queryForObject("SELECT COUNT(*) FROM worker_occupancy WHERE worker_id=? AND active=1 AND start_at<? AND end_at>?",Integer.class,workerId,leave.get("end_at"),leave.get("start_at"));
            if(busy!=null&&busy>0)throw new ApiException(HttpStatus.CONFLICT,"LEAVE_TASK_CONFLICT","请假时段存在已派任务，请先协调任务");
        }
        jdbc.update("UPDATE worker_leave SET status=?,decided_by=?,decided_at=? WHERE id=?",approve?"APPROVED":"REJECTED",adminId,LocalDateTime.now(ZoneId.of("Asia/Shanghai")),leaveId);
        audit.record(adminId, approve ? "WORKER_LEAVE_APPROVE" : "WORKER_LEAVE_REJECT", "WORKER_LEAVE", leaveId,
                (approve ? "批准" : "驳回") + "服务人员 #" + workerId + " 的请假申请");
    }

    public List<Map<String, Object>> tasks(long workerId) {
        return jdbc.queryForList("SELECT o.id AS order_id,o.item_name_snapshot,COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,o.street_name_snapshot,o.status,a.status AS assignment_status FROM assignment a JOIN service_order o ON o.id=a.order_id LEFT JOIN supplement_plan s ON s.order_id=o.id AND s.status IN ('ASSIGNED','IN_SERVICE','SUBMITTED') WHERE a.worker_id=? AND a.status IN ('PENDING','ACCEPTED') AND o.status IN ('WAITING_ACCEPTANCE','WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION') ORDER BY COALESCE(s.start_at,o.start_at),a.id", workerId);
    }

    public Map<String,Object> task(long workerId,long orderId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT o.id AS order_id,o.item_name_snapshot,o.category_snapshot,o.rule_snapshot,o.duration_minutes,o.street_name_snapshot,o.address_snapshot,COALESCE(s.start_at,o.start_at) AS start_at,COALESCE(s.end_at,o.end_at) AS end_at,o.status,a.status AS assignment_status,a.assigned_at,a.responded_at,e.full_name AS elder_name FROM assignment a JOIN service_order o ON o.id=a.order_id JOIN elder_profile e ON e.id=o.elder_id LEFT JOIN supplement_plan s ON s.order_id=o.id AND s.status IN ('ASSIGNED','IN_SERVICE','SUBMITTED') WHERE o.id=? AND a.worker_id=? AND a.status IN ('PENDING','ACCEPTED') AND o.status IN ('WAITING_ACCEPTANCE','WAITING_SERVICE','IN_SERVICE','WAITING_CONFIRMATION')",orderId,workerId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.FORBIDDEN,"TASK_NOT_AVAILABLE","当前没有此任务的有效派单");
        return rows.getFirst();
    }
}
