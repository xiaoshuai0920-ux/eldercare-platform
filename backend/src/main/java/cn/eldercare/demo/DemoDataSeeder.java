package cn.eldercare.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

import cn.eldercare.identity.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地演示数据集：账号、区域、服务项目、人员能力与排班、老人档案与家庭授权、
 * 覆盖全部订单状态的订单及其派单/履约/结算/异常/评价记录、站内通知与生活提醒。
 *
 * 规则：
 * 1) 只有 app.demo-data.enabled=true 时执行，且必须提供演示账号密码；
 * 2) 只增不改不删：已存在的业务数据一律复用，不覆盖用户改动（密码仅在 reset-passwords=true 时重置）；
 * 3) 全部写入在同一事务内，末尾做状态机不变量自检，任一不满足则整体回滚。
 */
@Component
public class DemoDataSeeder implements ApplicationRunner, Ordered {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final DemoSeedRegistry registry;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final boolean resetPasswords;
    private final String demoPassword;

    public DemoDataSeeder(JdbcTemplate jdbc, DemoSeedRegistry registry, PasswordEncoder encoder,
                          @Value("${app.demo-data.enabled:false}") boolean enabled,
                          @Value("${app.demo-data.reset-passwords:false}") boolean resetPasswords,
                          @Value("${app.demo-data.password:}") String demoPassword) {
        this.jdbc = jdbc;
        this.registry = registry;
        this.encoder = encoder;
        this.enabled = enabled;
        this.resetPasswords = resetPasswords;
        this.demoPassword = demoPassword;
    }

    @Override
    public int getOrder() { return 20; }

    private record Accounts(long admin, long family01, long family02, long worker01, long worker02, long worker03) { }
    private record Catalog(long east, long south, long wang, long meal, long cleaning, long escort) { }
    private record Elders(long zhang, long liu, long wang) { }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[DEMO] 演示数据开关关闭，跳过");
            return;
        }
        if (demoPassword == null || demoPassword.length() < 6) {
            log.warn("[DEMO] 未配置 app.demo-data.password（DEMO_ACCOUNT_PASSWORD），跳过演示数据");
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE).withSecond(0).withNano(0);
        Accounts accounts = seedAccounts();
        Catalog catalog = seedCatalog();
        seedWorkerProfile(accounts, catalog, now);
        Elders elders = seedFamily(accounts, catalog, now);
        seedOrders(accounts, catalog, elders, now);
        seedReminders(elders, accounts, now);
        seedAudit(accounts, elders, now);
        verifyInvariants();
        log.info("[DEMO] 演示数据就绪 accounts=6 orders={} 演示密码长度={}",
                registry.count("SELECT COUNT(*) FROM demo_seed_record WHERE entity_type='ORDER'"), demoPassword.length());
    }

    // ------------------------------------------------------------------ 账号

    private Accounts seedAccounts() {
        long admin = ensureAccount("admin", "系统管理员", "ADMIN", "ACTIVE", true);
        long family01 = ensureAccount("family01", "陈晓梅", "USER", "ACTIVE", true);
        long family02 = ensureAccount("family02", "陈建国", "USER", "ACTIVE", true);
        long worker01 = ensureAccount("worker01", "王秀兰", "WORKER", "ACTIVE", true);
        long worker02 = ensureAccount("worker02", "李国强", "WORKER", "ACTIVE", true);
        long worker03 = ensureAccount("worker03", "赵春燕", "WORKER", "DISABLED", false);
        return new Accounts(admin, family01, family02, worker01, worker02, worker03);
    }

    private long ensureAccount(String username, String displayName, String role, String status, boolean accepting) {
        String normalized = AuthService.normalizeUsername(username);
        Long existing = registry.findId("SELECT id FROM account WHERE username=?", normalized);
        if (existing != null) {
            if (resetPasswords) {
                jdbc.update("UPDATE account SET password_hash=?,status=?,accepting=?,failed_logins=0,locked_until=NULL WHERE id=?",
                        encoder.encode(demoPassword), status, accepting, existing);
            } else {
                jdbc.update("UPDATE account SET failed_logins=0,locked_until=NULL WHERE id=? AND role=? AND status='ACTIVE'", existing, role);
            }
            return existing;
        }
        long id = registry.insert("INSERT INTO account(username,email,display_name,password_hash,role,status,accepting) VALUES(?,?,?,?,?,?,?)",
                normalized, null, displayName, encoder.encode(demoPassword), role, status, accepting);
        jdbc.update("INSERT INTO audit_event(actor_id,action,target_type,target_id,summary) VALUES(?,?,?,?,?)",
                id, "ACCOUNT_SEED", "ACCOUNT", id, "创建演示账号");
        return id;
    }

    // ------------------------------------------------------------------ 目录

    private Catalog seedCatalog() {
        long city = ensureArea(null, "CITY", "示例市");
        long district = ensureArea(city, "DISTRICT", "安宁区");
        long east = ensureArea(district, "STREET", "东湖街道");
        long south = ensureArea(district, "STREET", "南湖街道");
        long wang = ensureArea(district, "STREET", "望京街道");

        long meal = ensureItem("item:meal", "MEAL", "上门助餐",
                "营养师配餐，含两荤一素与汤，服务人员上门协助就餐并整理餐后环境。",
                "FIXED", "45.00", 60, 120, "08:00:00", "18:00:00", 120,
                "固定价 45 元；服务时长 60–120 分钟；需提前 2 小时预约；服务时段 08:00–18:00。");
        long cleaning = ensureItem("item:cleaning", "CLEANING", "居家保洁",
                "客厅、卧室、厨卫日常清洁，可按需增加玻璃与深度除螨。",
                "HOURLY", "60.00", 60, 240, "08:00:00", "20:00:00", 240,
                "按小时计费 60 元/小时；服务时长 60–240 分钟；需提前 4 小时预约；服务时段 08:00–20:00。");
        long escort = ensureItem("item:escort", "ESCORT", "陪同就医",
                "陪诊人员全程陪同挂号、就诊、取药，并反馈就诊记录。",
                "FIXED", "180.00", 120, 240, "07:00:00", "17:00:00", 720,
                "固定价 180 元；服务时长 120–240 分钟；需提前 12 小时预约；服务时段 07:00–17:00。");

        for (long street : new long[]{east, south, wang}) {
            for (long item : new long[]{meal, cleaning, escort}) {
                jdbc.update("INSERT IGNORE INTO service_item_area(item_id,street_id) VALUES(?,?)", item, street);
            }
        }
        for (String tag : new String[]{"清淡饮食", "软食"}) jdbc.update("INSERT IGNORE INTO service_item_tag(item_id,tag) VALUES(?,?)", meal, tag);
        for (String tag : new String[]{"深度保洁", "除螨"}) jdbc.update("INSERT IGNORE INTO service_item_tag(item_id,tag) VALUES(?,?)", cleaning, tag);
        for (String tag : new String[]{"行动不便", "复诊"}) jdbc.update("INSERT IGNORE INTO service_item_tag(item_id,tag) VALUES(?,?)", escort, tag);
        return new Catalog(east, south, wang, meal, cleaning, escort);
    }

    private long ensureArea(Long parentId, String level, String name) {
        Long existing = registry.findId("SELECT id FROM service_area WHERE parent_id <=> ? AND name=? AND level=?",
                parentId, name, level);
        if (existing != null) return existing;
        return registry.insert("INSERT INTO service_area(parent_id,level,name,active) VALUES(?,?,?,1)", parentId, level, name);
    }

    private long ensureItem(String key, String category, String name, String description, String pricing,
                            String unitPrice, int minMinutes, int maxMinutes, String from, String to,
                            int advanceMinutes, String ruleText) {
        return registry.upsert(key, "SERVICE_ITEM", () -> registry.insert(
                "INSERT INTO service_item(category,name,description,pricing_model,unit_price,min_minutes,max_minutes,available_from,available_to,advance_minutes,rule_text,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,1)",
                category, name, description, pricing, new BigDecimal(unitPrice), minMinutes, maxMinutes,
                java.sql.Time.valueOf(from), java.sql.Time.valueOf(to), advanceMinutes, ruleText));
    }

    // ------------------------------------------------------------------ 人员

    private void seedWorkerProfile(Accounts accounts, Catalog catalog, LocalDateTime now) {
        String[][] capabilities = {
                {"worker01", "meal", "east"}, {"worker01", "meal", "south"},
                {"worker01", "cleaning", "east"}, {"worker01", "cleaning", "south"},
                {"worker01", "escort", "east"}, {"worker01", "escort", "south"},
                {"worker02", "meal", "south"}, {"worker02", "meal", "wang"},
                {"worker02", "escort", "south"}, {"worker02", "escort", "wang"},
                {"worker03", "cleaning", "east"}
        };
        for (String[] row : capabilities) {
            long worker = "worker01".equals(row[0]) ? accounts.worker01() : "worker02".equals(row[0]) ? accounts.worker02() : accounts.worker03();
            long item = "meal".equals(row[1]) ? catalog.meal() : "cleaning".equals(row[1]) ? catalog.cleaning() : catalog.escort();
            long street = "east".equals(row[2]) ? catalog.east() : "south".equals(row[2]) ? catalog.south() : catalog.wang();
            jdbc.update("INSERT IGNORE INTO worker_capability(worker_id,item_id,street_id,active) VALUES(?,?,?,1)", worker, item, street);
        }
        for (int weekday = 1; weekday <= 7; weekday++) {
            ensureSchedule(accounts.worker01(), weekday, "08:00:00", "18:00:00");
            ensureSchedule(accounts.worker02(), weekday, "08:00:00", "20:00:00");
        }
        ensureLeave(accounts.worker01(), now.plusDays(3).withHour(9).withMinute(0), now.plusDays(3).withHour(12).withMinute(0),
                "家属陪同体检", "APPROVED", accounts.admin());
        ensureLeave(accounts.worker02(), now.plusDays(5).withHour(9).withMinute(0), now.plusDays(5).withHour(18).withMinute(0),
                "回乡办事", "PENDING", null);
    }

    private void ensureSchedule(long workerId, int weekday, String start, String end) {
        if (registry.count("SELECT COUNT(*) FROM worker_schedule WHERE worker_id=? AND weekday=? AND start_time=? AND end_time=?",
                workerId, weekday, java.sql.Time.valueOf(start), java.sql.Time.valueOf(end)) > 0) return;
        jdbc.update("INSERT INTO worker_schedule(worker_id,weekday,start_time,end_time) VALUES(?,?,?,?)",
                workerId, weekday, java.sql.Time.valueOf(start), java.sql.Time.valueOf(end));
    }

    private void ensureLeave(long workerId, LocalDateTime start, LocalDateTime end, String reason, String status, Long decidedBy) {
        if (registry.count("SELECT COUNT(*) FROM worker_leave WHERE worker_id=? AND start_at=?", workerId, start) > 0) return;
        jdbc.update("INSERT INTO worker_leave(worker_id,start_at,end_at,reason,status,decided_by,decided_at) VALUES(?,?,?,?,?,?,?)",
                workerId, start, end, reason, status, decidedBy, "APPROVED".equals(status) ? start.minusDays(1) : null);
    }

    // ------------------------------------------------------------------ 档案与家庭

    private Elders seedFamily(Accounts accounts, Catalog catalog, LocalDateTime now) {
        // 陈家：三位老人是亲兄妹（德字辈）。长姐陈德珍招赘，女儿陈晓梅随母姓；三弟陈德远的儿子是陈建国。
        // 陈晓梅与陈建国为表兄妹，二姑陈德芳无子女，由两家共同照护。
        long zhang = ensureElder("elder:zhang", "陈德芳", "1943-05-12", accounts.family01());
        long liu = ensureElder("elder:liu", "陈德远", "1948-11-03", accounts.family02());
        long wang = ensureElder("elder:wang", "陈德珍", "1939-02-27", accounts.family01());

        grant(zhang, accounts.family01(), true, true, true, true, true, true, true, true);
        grant(zhang, accounts.family02(), true, true, true, false, true, true, false, false);
        grant(liu, accounts.family02(), true, true, true, true, true, true, true, true);
        // 外甥女对舅舅只授查看权，用于演示“授权范围不同”
        grant(liu, accounts.family01(), true, false, false, false, false, false, false, false);
        // 侄子对姑母同样只授查看权，与“外甥女看舅舅”对称
        grant(wang, accounts.family02(), true, false, false, false, false, false, false, false);
        // 陈德珍由女儿陈晓梅建档并下单，必须同时具备确认/取消/评价权限，否则「本人下的单自己无法确认」会卡住流程
        grant(wang, accounts.family01(), true, true, true, true, true, true, true, true);

        for (String tag : new String[]{"清淡饮食", "软食"}) {
            jdbc.update("INSERT IGNORE INTO elder_preference_tag(elder_id,tag) VALUES(?,?)", zhang, tag);
        }
        jdbc.update("INSERT IGNORE INTO elder_preference_tag(elder_id,tag) VALUES(?,?)", liu, "深度保洁");
        jdbc.update("INSERT IGNORE INTO elder_preference_tag(elder_id,tag) VALUES(?,?)", wang, "行动不便");

        ensureInvitation("invite:pending-liu", liu, accounts.family02(), accounts.family01(), "PENDING", now.minusDays(1), now.plusDays(6));
        ensureInvitation("invite:accepted-zhang", zhang, accounts.family01(), accounts.family02(), "ACCEPTED", now.minusDays(12), now.plusDays(30));

        ensureIdentityClaim("claim:liu-approved", liu, accounts.family02(), "APPROVED", accounts.admin());
        ensureIdentityClaim("claim:wang-pending", wang, accounts.family01(), "PENDING", null);

        if (registry.count("SELECT COUNT(*) FROM family_dispute WHERE elder_id=?", zhang) == 0) {
            long disputeId = registry.insert("INSERT INTO family_dispute(elder_id,reporter_id,reason,status,reported_at) VALUES(?,?,?,'OPEN',?)",
                    zhang, accounts.family02(), "希望确认本人对档案的管理权限，请管理员协助核实。", now.minusDays(2));
            registry.remember("family-dispute:zhang", "FAMILY_DISPUTE", disputeId);
        }
        return new Elders(zhang, liu, wang);
    }

    private long ensureElder(String key, String name, String birthDate, long creator) {
        return registry.upsert(key, "ELDER", () -> registry.insert(
                "INSERT INTO elder_profile(full_name,birth_date,contact_note,created_by,status) VALUES(?,?,?,?,'ACTIVE')",
                name, java.sql.Date.valueOf(birthDate), null, creator));
    }

    private void grant(long elderId, long accountId, boolean view, boolean book, boolean reminder, boolean cancel,
                       boolean confirm, boolean review, boolean fee, boolean family) {
        jdbc.update("INSERT INTO family_grant(elder_id,account_id,can_view_service,can_book,can_manage_reminder,can_cancel,can_confirm,can_review,can_confirm_fee,can_manage_family,status) VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE') " +
                        "ON DUPLICATE KEY UPDATE can_view_service=VALUES(can_view_service),can_book=VALUES(can_book),can_manage_reminder=VALUES(can_manage_reminder),can_cancel=VALUES(can_cancel),can_confirm=VALUES(can_confirm),can_review=VALUES(can_review),can_confirm_fee=VALUES(can_confirm_fee),can_manage_family=VALUES(can_manage_family),status='ACTIVE',revoked_at=NULL",
                elderId, accountId, view, book, reminder, cancel, confirm, review, fee, family);
    }

    private void ensureInvitation(String key, long elderId, long inviterId, long inviteeId, String status,
                                  LocalDateTime createdAt, LocalDateTime expiresAt) {
        registry.upsert(key, "INVITATION", () -> registry.insert(
                "INSERT INTO family_invitation(elder_id,inviter_id,invitee_account_id,invitee_email,can_view_service,can_book,can_manage_reminder,can_cancel,can_confirm,can_review,can_confirm_fee,can_manage_family,status,expires_at,responded_at,created_at) VALUES(?,?,?,NULL,1,1,0,0,0,0,0,0,?,?,?,?)",
                elderId, inviterId, inviteeId, status, expiresAt,
                "ACCEPTED".equals(status) ? createdAt.plusHours(3) : null, createdAt));
    }

    private void ensureIdentityClaim(String key, long elderId, long accountId, String status, Long reviewer) {
        registry.upsert(key, "IDENTITY_CLAIM", () -> {
            LocalDateTime requested = LocalDateTime.now(ZONE).minusDays(6);
            return registry.insert("INSERT INTO elder_identity_claim(elder_id,account_id,status,requested_at,reviewed_by,review_note,reviewed_at) VALUES(?,?,?,?,?,?,?)",
                    elderId, accountId, status, requested,
                    "APPROVED".equals(status) ? reviewer : null,
                    "APPROVED".equals(status) ? "已核验本人关系" : null,
                    "APPROVED".equals(status) ? requested.plusDays(1) : null);
        });
    }

    // ------------------------------------------------------------------ 订单

    private void seedOrders(Accounts accounts, Catalog catalog, Elders elders, LocalDateTime now) {
        // 1 已完成 · 待结算 · 有可见评价
        long o1 = createOrder("order:completed-unpaid", accounts.family01(), elders.zhang(), catalog.meal(), catalog.east(),
                90, now.minusDays(6).withHour(9).withMinute(0), "安宁区东湖街道梧桐里 3 号楼 502");
        if (o1 > 0) {
            long a1 = assign(o1, accounts.worker01(), accounts.admin(), "ACCEPTED", false,
                    now.minusDays(6).withHour(9).withMinute(0), now.minusDays(6).withHour(10).withMinute(30), now.minusDays(7).withHour(10).withMinute(0));
            fulfill(o1, a1, now.minusDays(6).withHour(9).withMinute(5), now.minusDays(6).withHour(10).withMinute(35), now.minusDays(6).withHour(11).withMinute(0), "CONFIRMED");
            settlement(o1, "45.00", "WAITING_METHOD", null, null);
            review(o1, accounts.family01(), 5, "王阿姨很细心，饭菜温度合适，老人吃得很顺口。", "VISIBLE", null, null);
            history(o1, accounts.family01(), accounts.worker01(), "COMPLETED", now.minusDays(6));
        }
        // 2 已完成 · 线下已结算 · 评价被隐藏
        long o2 = createOrder("order:settled-offline", accounts.family01(), elders.zhang(), catalog.cleaning(), catalog.east(),
                120, now.minusDays(9).withHour(14).withMinute(0), "安宁区东湖街道梧桐里 3 号楼 502");
        if (o2 > 0) {
            long a2 = assign(o2, accounts.worker01(), accounts.admin(), "ACCEPTED", false,
                    now.minusDays(9).withHour(14).withMinute(0), now.minusDays(9).withHour(16).withMinute(0), now.minusDays(10).withHour(9).withMinute(0));
            fulfill(o2, a2, now.minusDays(9).withHour(14).withMinute(0), now.minusDays(9).withHour(16).withMinute(5), now.minusDays(9).withHour(17).withMinute(0), "CONFIRMED");
            long s2 = settlement(o2, "120.00", "SETTLED", "OFFLINE", now.minusDays(8).withHour(10).withMinute(0));
            settlementEvent(s2, accounts.admin(), "OFFLINE_SETTLED", "管理员登记线下收款", now.minusDays(8).withHour(10).withMinute(0));
            review(o2, accounts.family01(), 3, "清洁整体可以，但厨房角落还有灰尘。", "HIDDEN", accounts.admin(), "含未经核实的服务人员个人信息");
            history(o2, accounts.family01(), accounts.worker01(), "COMPLETED", now.minusDays(9));
        }
        // 3 已完成 · 事后争议待处理
        long o3 = createOrder("order:dispute-open", accounts.family02(), elders.liu(), catalog.cleaning(), catalog.south(),
                180, now.minusDays(4).withHour(10).withMinute(0), "安宁区南湖街道柳岸小区 8 号楼 201");
        if (o3 > 0) {
            long a3 = assign(o3, accounts.worker02(), accounts.admin(), "ACCEPTED", false,
                    now.minusDays(4).withHour(10).withMinute(0), now.minusDays(4).withHour(13).withMinute(0), now.minusDays(5).withHour(11).withMinute(0));
            fulfill(o3, a3, now.minusDays(4).withHour(10).withMinute(10), now.minusDays(4).withHour(13).withMinute(5), now.minusDays(4).withHour(14).withMinute(0), "CONFIRMED");
            long s3 = settlement(o3, "180.00", "SETTLED", "OFFLINE", now.minusDays(3).withHour(9).withMinute(0));
            settlementEvent(s3, accounts.admin(), "OFFLINE_SETTLED", "管理员登记线下收款", now.minusDays(3).withHour(9).withMinute(0));
            registry.insert("INSERT INTO order_dispute(order_id,opened_by,reason,status,created_at) VALUES(?,?,?,'OPEN',?)",
                    o3, accounts.family02(), "阳台与窗框没有按约定清洁，希望核实并说明。", now.minusDays(2));
            history(o3, accounts.family02(), accounts.worker02(), "COMPLETED", now.minusDays(4));
        }
        // 4 待用户确认（提交已 26 小时，用于超时未确认演示）
        long o4 = createOrder("order:waiting-confirm", accounts.family01(), elders.zhang(), catalog.cleaning(), catalog.east(),
                120, now.minusDays(2).withHour(9).withMinute(0), "安宁区东湖街道梧桐里 3 号楼 502");
        if (o4 > 0) {
            long a4 = assign(o4, accounts.worker01(), accounts.admin(), "ACCEPTED", true,
                    now.minusDays(2).withHour(9).withMinute(0), now.minusDays(2).withHour(11).withMinute(0), now.minusDays(3).withHour(15).withMinute(0));
            fulfill(o4, a4, now.minusDays(2).withHour(9).withMinute(0), now.minusHours(26), null, "SUBMITTED");
            history(o4, accounts.family01(), accounts.worker01(), "SUBMITTED", now.minusDays(2));
        }
        // 5 服务中
        long o5 = createOrder("order:in-service", accounts.family01(), elders.zhang(), catalog.meal(), catalog.east(),
                90, now.minusMinutes(40), "安宁区东湖街道梧桐里 3 号楼 502");
        if (o5 > 0) {
            long a5 = assign(o5, accounts.worker01(), accounts.admin(), "ACCEPTED", true,
                    now.minusMinutes(40), now.plusMinutes(50), now.minusDays(1).withHour(9).withMinute(0));
            fulfill(o5, a5, now.minusMinutes(40), null, null, "IN_PROGRESS");
            history(o5, accounts.family01(), accounts.worker01(), "STARTED", now);
        }
        // 6 待服务（明日）
        long o6 = createOrder("order:waiting-service", accounts.family01(), elders.wang(), catalog.meal(), catalog.wang(),
                60, now.plusDays(1).withHour(10).withMinute(0), "安宁区望京街道松林苑 2 号楼 1103");
        if (o6 > 0) {
            long a6 = assign(o6, accounts.worker02(), accounts.admin(), "ACCEPTED", true,
                    now.plusDays(1).withHour(10).withMinute(0), now.plusDays(1).withHour(11).withMinute(0), now.minusHours(5));
        }
        // 7 待接单（后天）
        long o7 = createOrder("order:waiting-accept", accounts.family02(), elders.liu(), catalog.cleaning(), catalog.south(),
                120, now.plusDays(2).withHour(14).withMinute(0), "安宁区南湖街道柳岸小区 8 号楼 201");
        if (o7 > 0) {
            assign(o7, accounts.worker01(), accounts.admin(), "PENDING", true,
                    now.plusDays(2).withHour(14).withMinute(0), now.plusDays(2).withHour(16).withMinute(0), now.minusHours(2));
        }
        // 8 待派单（3 小时后）
        long o8 = createOrder("order:waiting-assign", accounts.family01(), elders.zhang(), catalog.meal(), catalog.east(),
                60, now.plusHours(3), "安宁区东湖街道梧桐里 3 号楼 502");
        // 9 已取消
        long o9 = createOrder("order:cancelled", accounts.family01(), elders.wang(), catalog.meal(), catalog.wang(),
                60, now.minusDays(3).withHour(15).withMinute(0), "安宁区望京街道松林苑 2 号楼 1103");
        if (o9 > 0) {
            long a9 = assign(o9, accounts.worker02(), accounts.admin(), "RELEASED", false,
                    now.minusDays(3).withHour(15).withMinute(0), now.minusDays(3).withHour(16).withMinute(0), now.minusDays(4).withHour(10).withMinute(0));
            event(o9, accounts.family01(), "CANCEL", "WAITING_ACCEPTANCE", "CANCELLED", "老人临时去医院复查，取消本次预约", now.minusDays(3).withHour(9).withMinute(0));
        }
        // 10 中断待协调
        long o10 = createOrder("order:interrupted", accounts.family02(), elders.liu(), catalog.cleaning(), catalog.south(),
                120, now.minusDays(1).withHour(10).withMinute(0), "安宁区南湖街道柳岸小区 8 号楼 201");
        if (o10 > 0) {
            long a10 = assign(o10, accounts.worker02(), accounts.admin(), "RELEASED", false,
                    now.minusDays(1).withHour(10).withMinute(0), now.minusDays(1).withHour(12).withMinute(0), now.minusDays(2).withHour(16).withMinute(0));
            fulfill(o10, a10, now.minusDays(1).withHour(10).withMinute(5), null, null, "INTERRUPTED");
            long incident = registry.insert("INSERT INTO service_incident(order_id,reported_by,reason,status,created_at) VALUES(?,?,?,'OPEN',?)",
                    o10, accounts.worker02(), "服务中发现老人血压偏高，已协助联系家属并中止保洁。", now.minusDays(1).withHour(10).withMinute(40));
            registry.remember("incident:interrupted", "INCIDENT", incident);
            event(o10, accounts.worker02(), "INTERRUPT", "IN_SERVICE", "INTERRUPTED", "服务中断：老人身体不适", now.minusDays(1).withHour(10).withMinute(40));
        }
        // 11 中断 · 费用调整待用户确认
        long o11 = createOrder("order:fee-pending", accounts.family01(), elders.zhang(), catalog.cleaning(), catalog.east(),
                120, now.minusDays(2).withHour(14).withMinute(0), "安宁区东湖街道梧桐里 3 号楼 502");
        if (o11 > 0) {
            long a11 = assign(o11, accounts.worker01(), accounts.admin(), "RELEASED", false,
                    now.minusDays(2).withHour(14).withMinute(0), now.minusDays(2).withHour(16).withMinute(0), now.minusDays(3).withHour(9).withMinute(0));
            fulfill(o11, a11, now.minusDays(2).withHour(14).withMinute(5), null, null, "INTERRUPTED");
            long incident = registry.insert("INSERT INTO service_incident(order_id,reported_by,reason,status,created_at) VALUES(?,?,?,'OPEN',?)",
                    o11, accounts.worker01(), "老人家中临时停水，保洁只完成客厅与卧室。", now.minusDays(2).withHour(14).withMinute(50));
            registry.remember("incident:fee-pending", "INCIDENT", incident);
            registry.insert("INSERT INTO fee_adjustment(order_id,proposed_by,original_amount,proposed_amount,reason,status,created_at) VALUES(?,?,?,?,?,'PENDING',?)",
                    o11, accounts.admin(), new BigDecimal("120.00"), new BigDecimal("80.00"), "停水导致厨房与卫生间未清洁，按已完成区域调整费用。", now.minusDays(2).withHour(15).withMinute(30));
            event(o11, accounts.worker01(), "INTERRUPT", "IN_SERVICE", "INTERRUPTED", "服务中断：临时停水", now.minusDays(2).withHour(14).withMinute(50));
        }
        // 12 异常结案 · 费用已调整 · 待结算
        long o12 = createOrder("order:abnormal-closed", accounts.family02(), elders.liu(), catalog.cleaning(), catalog.south(),
                120, now.minusDays(5).withHour(9).withMinute(0), "安宁区南湖街道柳岸小区 8 号楼 201");
        if (o12 > 0) {
            long a12 = assign(o12, accounts.worker02(), accounts.admin(), "RELEASED", false,
                    now.minusDays(5).withHour(9).withMinute(0), now.minusDays(5).withHour(11).withMinute(0), now.minusDays(6).withHour(10).withMinute(0));
            fulfill(o12, a12, now.minusDays(5).withHour(9).withMinute(5), null, null, "INTERRUPTED");
            long incident = registry.insert("INSERT INTO service_incident(order_id,reported_by,reason,status,resolved_by,resolution,created_at,resolved_at) VALUES(?,?,?,'RESOLVED',?,?,?,?)",
                    o12, accounts.worker02(), "老人要求提前结束服务，厨房未清洁。", accounts.admin(), "与家属核实后按实际完成区域调整费用并结案",
                    now.minusDays(5).withHour(10).withMinute(0), now.minusDays(4).withHour(16).withMinute(0));
            registry.remember("incident:abnormal", "INCIDENT", incident);
            registry.insert("INSERT INTO fee_adjustment(order_id,proposed_by,original_amount,proposed_amount,reason,status,decided_by,decided_at,created_at) VALUES(?,?,?,?,?,'APPROVED',?,?,?)",
                    o12, accounts.admin(), new BigDecimal("120.00"), new BigDecimal("90.00"), "厨房区域未完成，按已完成面积调整。",
                    accounts.family02(), now.minusDays(4).withHour(17).withMinute(0), now.minusDays(5).withHour(17).withMinute(0));
            updateAmount(o12, "90.00");
            settlement(o12, "90.00", "WAITING_METHOD", null, null);
            event(o12, accounts.admin(), "ABNORMAL_CLOSE", "INTERRUPTED", "ABNORMAL_CLOSED", "费用调整已确认，订单异常结案", now.minusDays(4).withHour(17).withMinute(5));
        }
        // 13 中断后补服务已完成（两次履约）
        long o13 = createOrder("order:supplement-done", accounts.family01(), elders.wang(), catalog.escort(), catalog.wang(),
                120, now.minusDays(7).withHour(9).withMinute(0), "安宁区望京街道松林苑 2 号楼 1103");
        if (o13 > 0) {
            long a13a = assign(o13, accounts.worker02(), accounts.admin(), "RELEASED", false,
                    now.minusDays(7).withHour(9).withMinute(0), now.minusDays(7).withHour(11).withMinute(0), now.minusDays(8).withHour(10).withMinute(0));
            fulfill(o13, a13a, now.minusDays(7).withHour(9).withMinute(10), null, null, "INTERRUPTED");
            long incident = registry.insert("INSERT INTO service_incident(order_id,reported_by,reason,status,resolved_by,resolution,created_at,resolved_at) VALUES(?,?,?,'RESOLVED',?,?,?,?)",
                    o13, accounts.worker02(), "医院临时停诊，陪诊未开始即中断。", accounts.admin(), "已安排补服务并完成",
                    now.minusDays(7).withHour(9).withMinute(30), now.minusDays(5).withHour(17).withMinute(0));
            registry.remember("incident:supplement", "INCIDENT", incident);
            long plan = registry.insert("INSERT INTO supplement_plan(order_id,created_by,reason,start_at,end_at,status,created_at) VALUES(?,?,?,?,?,'CONFIRMED',?)",
                    o13, accounts.admin(), "医院停诊，重新安排陪同复诊", now.minusDays(5).withHour(15).withMinute(0),
                    now.minusDays(5).withHour(17).withMinute(0), now.minusDays(6).withHour(9).withMinute(0));
            registry.remember("supplement:done", "SUPPLEMENT", plan);
            long a13b = assignWithTimes(o13, accounts.worker01(), accounts.admin(), "ACCEPTED", false,
                    now.minusDays(5).withHour(15).withMinute(0), now.minusDays(5).withHour(17).withMinute(0), now.minusDays(6).withHour(10).withMinute(0));
            fulfill(o13, a13b, now.minusDays(5).withHour(15).withMinute(5), now.minusDays(5).withHour(17).withMinute(5), now.minusDays(5).withHour(18).withMinute(0), "CONFIRMED");
            long s13 = settlement(o13, "180.00", "SETTLED", "OFFLINE", now.minusDays(4).withHour(11).withMinute(0));
            settlementEvent(s13, accounts.admin(), "OFFLINE_SETTLED", "补服务完成后管理员登记线下收款", now.minusDays(4).withHour(11).withMinute(0));
            event(o13, accounts.worker02(), "INTERRUPT", "IN_SERVICE", "INTERRUPTED", "服务中断：医院停诊", now.minusDays(7).withHour(9).withMinute(30));
            event(o13, accounts.admin(), "SUPPLEMENT_SCHEDULE", "INTERRUPTED", "WAITING_ASSIGNMENT", "安排补服务并派单", now.minusDays(6).withHour(10).withMinute(0));
            history(o13, accounts.family01(), accounts.worker01(), "COMPLETED", now.minusDays(5));
        }
        // 14 已完成 · 模拟支付处理中（未在控制台登记）
        long o14 = createOrder("order:payment-processing", accounts.family01(), elders.zhang(), catalog.meal(), catalog.east(),
                60, now.minusDays(8).withHour(11).withMinute(0), "安宁区东湖街道梧桐里 3 号楼 502");
        if (o14 > 0) {
            long a14 = assign(o14, accounts.worker01(), accounts.admin(), "ACCEPTED", false,
                    now.minusDays(8).withHour(11).withMinute(0), now.minusDays(8).withHour(12).withMinute(0), now.minusDays(9).withHour(9).withMinute(0));
            fulfill(o14, a14, now.minusDays(8).withHour(11).withMinute(0), now.minusDays(8).withHour(12).withMinute(5), now.minusDays(8).withHour(13).withMinute(0), "CONFIRMED");
            long s14 = settlement(o14, "45.00", "PROCESSING", "SIMULATED", null);
            paymentAttempt(s14, "demo-attempt-0001", "45.00", 1, "UNREGISTERED");
            settlementEvent(s14, accounts.family01(), "PAYMENT_CREATE", "创建模拟支付尝试 demo-attempt-0001", now.minusDays(8).withHour(13).withMinute(10));
            history(o14, accounts.family01(), accounts.worker01(), "COMPLETED", now.minusDays(8));
        }
        // 15 已完成 · 模拟支付失败，可重试
        long o15 = createOrder("order:payment-failed", accounts.family01(), elders.zhang(), catalog.meal(), catalog.east(),
                90, now.minusDays(10).withHour(9).withMinute(0), "安宁区东湖街道梧桐里 3 号楼 502");
        if (o15 > 0) {
            long a15 = assign(o15, accounts.worker01(), accounts.admin(), "ACCEPTED", false,
                    now.minusDays(10).withHour(9).withMinute(0), now.minusDays(10).withHour(10).withMinute(30), now.minusDays(11).withHour(9).withMinute(0));
            fulfill(o15, a15, now.minusDays(10).withHour(9).withMinute(0), now.minusDays(10).withHour(10).withMinute(35), now.minusDays(10).withHour(11).withMinute(0), "CONFIRMED");
            long s15 = settlement(o15, "45.00", "FAILED", "SIMULATED", null);
            paymentAttempt(s15, "demo-attempt-0002", "45.00", 1, "CANCELLED");
            settlementEvent(s15, accounts.admin(), "PAYMENT_FAILED", "控制台登记的交易已取消，可重新发起", now.minusDays(10).withHour(12).withMinute(0));
            history(o15, accounts.family01(), accounts.worker01(), "COMPLETED", now.minusDays(10));
        }
        markStatus("order:completed-unpaid", "COMPLETED");
        markStatus("order:settled-offline", "COMPLETED");
        markStatus("order:dispute-open", "COMPLETED");
        markStatus("order:waiting-confirm", "WAITING_CONFIRMATION");
        markStatus("order:in-service", "IN_SERVICE");
        markStatus("order:waiting-service", "WAITING_SERVICE");
        markStatus("order:waiting-accept", "WAITING_ACCEPTANCE");
        markStatus("order:waiting-assign", "WAITING_ASSIGNMENT");
        markStatus("order:cancelled", "CANCELLED");
        markStatus("order:interrupted", "INTERRUPTED");
        markStatus("order:fee-pending", "INTERRUPTED");
        markStatus("order:abnormal-closed", "ABNORMAL_CLOSED");
        markStatus("order:supplement-done", "COMPLETED");
        markStatus("order:payment-processing", "COMPLETED");
        markStatus("order:payment-failed", "COMPLETED");
        seedNotifications(accounts, elders, now);
    }

    /** 只在仍处于建单初始状态时落最终状态，重复执行不会覆盖后续人工变更。 */
    private void markStatus(String key, String status) {
        Long orderId = registry.recall(key);
        if (orderId != null) {
            jdbc.update("UPDATE service_order SET status=? WHERE id=? AND status='WAITING_ASSIGNMENT'", status, orderId);
        }
    }

    /** 返回订单 id；已存在时返回 -1，调用方据此跳过关联数据。 */
    private long createOrder(String key, long creator, long elder, long item, long street, int duration,
                             LocalDateTime start, String address) {
        if (registry.recall(key) != null) return -1;
        var itemRow = jdbc.queryForMap("SELECT name,category,rule_text,pricing_model,unit_price FROM service_item WHERE id=?", item);
        String pricing = (String) itemRow.get("pricing_model");
        BigDecimal unitPrice = (BigDecimal) itemRow.get("unit_price");
        BigDecimal amount = "HOURLY".equals(pricing)
                ? unitPrice.multiply(BigDecimal.valueOf(duration)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                : unitPrice.setScale(2, RoundingMode.HALF_UP);
        String streetName = jdbc.queryForObject(
                "SELECT CONCAT(c.name,' ',d.name,' ',s.name) FROM service_area s JOIN service_area d ON d.id=s.parent_id JOIN service_area c ON c.id=d.parent_id WHERE s.id=?",
                String.class, street);
        LocalDateTime end = start.plusMinutes(duration);
        long id = registry.insert("INSERT INTO service_order(creator_id,elder_id,item_id,item_name_snapshot,category_snapshot,rule_snapshot,pricing_model_snapshot,unit_price_snapshot,duration_minutes,original_amount,payable_amount,street_id,street_name_snapshot,address_snapshot,start_at,end_at,status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'WAITING_ASSIGNMENT',?)",
                creator, elder, item, itemRow.get("name"), itemRow.get("category"), itemRow.get("rule_text"), pricing,
                unitPrice, duration, amount, amount, street, streetName, address, start, end, start.minusDays(1));
        registry.remember(key, "ORDER", id);
        event(id, creator, "CREATE", null, "WAITING_ASSIGNMENT", "预约已创建，等待人工派单", start.minusDays(1).plusMinutes(2));
        return id;
    }

    private long assign(long orderId, long workerId, long adminId, String status, boolean occupancyActive,
                        LocalDateTime start, LocalDateTime end, LocalDateTime assignedAt) {
        return assignWithTimes(orderId, workerId, adminId, status, occupancyActive, start, end, assignedAt);
    }

    private long assignWithTimes(long orderId, long workerId, long adminId, String status, boolean occupancyActive,
                                 LocalDateTime start, LocalDateTime end, LocalDateTime assignedAt) {
        long id = registry.insert("INSERT INTO assignment(order_id,worker_id,status,assigned_by,assigned_at,responded_at) VALUES(?,?,?,?,?,?)",
                orderId, workerId, status, adminId, assignedAt,
                "PENDING".equals(status) ? null : assignedAt.plusMinutes(20));
        jdbc.update("INSERT INTO worker_occupancy(assignment_id,worker_id,start_at,end_at,active) VALUES(?,?,?,?,?)",
                id, workerId, start, end, occupancyActive);
        event(orderId, adminId, "ASSIGN", "WAITING_ASSIGNMENT", "WAITING_ACCEPTANCE", "管理员派单", assignedAt);
        if (!"PENDING".equals(status)) {
            event(orderId, workerId, "ACCEPT", "WAITING_ACCEPTANCE", "WAITING_SERVICE", "服务人员已接单", assignedAt.plusMinutes(20));
        }
        return id;
    }

    private void fulfill(long orderId, long assignmentId, LocalDateTime started, LocalDateTime submitted,
                         LocalDateTime confirmed, String status) {
        registry.insert("INSERT INTO fulfillment(order_id,assignment_id,started_at,submitted_at,confirmed_at,status) VALUES(?,?,?,?,?,?)",
                orderId, assignmentId, started, submitted, confirmed, status);
    }

    private long settlement(long orderId, String amount, String status, String method, LocalDateTime settledAt) {
        return registry.insert("INSERT INTO settlement(order_id,amount,version,method,status,settled_at) VALUES(?,?,1,?,?,?)",
                orderId, new BigDecimal(amount), method, status, settledAt);
    }

    private void settlementEvent(long settlementId, long actorId, String action, String note, LocalDateTime at) {
        registry.insert("INSERT INTO settlement_event(settlement_id,actor_id,action,note,created_at) VALUES(?,?,?,?,?)",
                settlementId, actorId, action, note, at);
    }

    private void paymentAttempt(long settlementId, String ref, String amount, int version, String remoteStatus) {
        registry.insert("INSERT INTO payment_attempt(settlement_id,attempt_ref,amount,settlement_version,remote_status,created_at) VALUES(?,?,?,?,?,?)",
                settlementId, ref, new BigDecimal(amount), version, remoteStatus, LocalDateTime.now(ZONE).minusDays(8));
    }

    private void review(long orderId, long authorId, int stars, String content, String visibility, Long moderator, String reason) {
        registry.insert("INSERT INTO service_review(order_id,author_id,stars,content,visibility,moderated_by,moderation_reason,moderated_at) VALUES(?,?,?,?,?,?,?,?)",
                orderId, authorId, stars, content, visibility, moderator, reason,
                moderator == null ? null : LocalDateTime.now(ZONE).minusDays(2));
    }

    private void updateAmount(long orderId, String amount) {
        jdbc.update("UPDATE service_order SET payable_amount=? WHERE id=?", new BigDecimal(amount), orderId);
    }

    private void event(long orderId, long actorId, String action, String from, String to, String note, LocalDateTime at) {
        registry.insert("INSERT INTO order_event(order_id,actor_id,action,old_status,new_status,note,created_at) VALUES(?,?,?,?,?,?,?)",
                orderId, actorId, action, from, to, note, at);
    }

    /** 补全履约链路事件，使订单详情的时间线完整。stage: STARTED / SUBMITTED / COMPLETED。 */
    private void history(long orderId, long creator, Long worker, String stage, LocalDateTime base) {
        if (worker == null || "NONE".equals(stage)) return;
        event(orderId, worker, "START", "WAITING_SERVICE", "IN_SERVICE", "开始服务", base.plusMinutes(5));
        if ("SUBMITTED".equals(stage) || "COMPLETED".equals(stage)) {
            event(orderId, worker, "SUBMIT_COMPLETION", "IN_SERVICE", "WAITING_CONFIRMATION", "已提交完成，等待用户确认", base.plusHours(1));
        }
        if ("COMPLETED".equals(stage)) {
            event(orderId, creator, "CONFIRM", "WAITING_CONFIRMATION", "COMPLETED", "用户确认服务完成", base.plusHours(2));
        }
    }

    // ------------------------------------------------------------------ 通知 / 提醒 / 审计

    private void seedNotifications(Accounts accounts, Elders elders, LocalDateTime now) {
        Long order4 = registry.recall("order:waiting-confirm");
        Long order5 = registry.recall("order:in-service");
        Long order6 = registry.recall("order:waiting-service");
        Long order7 = registry.recall("order:waiting-accept");
        Long order8 = registry.recall("order:waiting-assign");
        Long order10 = registry.recall("order:interrupted");
        Long order11 = registry.recall("order:fee-pending");
        Long order1 = registry.recall("order:completed-unpaid");
        Long order3 = registry.recall("order:dispute-open");

        notifyOnce("notify:family01-confirm", accounts.family01(), eventId(order4), null, "保洁服务待确认",
                "服务人员已提交完成，请核对后确认，确认后进入结算。", "/user/orders", "ACTION", now.minusHours(26));
        notifyOnce("notify:family01-in-service", accounts.family01(), eventId(order5), null, "助餐服务进行中",
                "服务人员已开始服务，预计 50 分钟后完成。", "/user/orders", "NORMAL", now.minusMinutes(40));
        notifyOnce("notify:family01-settlement", accounts.family01(), eventId(order1), null, "订单待结算",
                "订单已完成，请选择结算方式。", "/user/orders", "ACTION", now.minusDays(6).withHour(11).withMinute(5));
        notifyOnce("notify:family01-fee", accounts.family01(), eventId(order11), null, "异常费用待确认",
                "管理员提出费用调整 120.00 → 80.00 元，请核对后确认或拒绝。", "/user/orders", "HIGH", now.minusDays(2).withHour(15).withMinute(35));
        notifyOnce("notify:worker01-new-task", accounts.worker01(), eventId(order7), null, "新的派单待接单",
                "南湖街道保洁任务待接单，请及时处理。", "/worker", "ACTION", now.minusHours(2));
        notifyOnce("notify:worker01-interrupt", accounts.worker01(), eventId(order11), null, "服务中断记录",
                "订单中断已记录，等待管理员协调。", "/worker", "NORMAL", now.minusDays(2).withHour(14).withMinute(55));
        notifyOnce("notify:worker02-service", accounts.worker02(), eventId(order6), null, "明日服务提醒",
                "明日 10:00 望京街道助餐服务，请提前确认路线。", "/worker", "NORMAL", now.minusHours(5));
        notifyOnce("notify:worker02-interrupt", accounts.worker02(), eventId(order10), null, "服务中断待协调",
                "订单 #" + order10 + " 因老人身体不适中断，请等待管理员安排。", "/worker", "HIGH", now.minusDays(1).withHour(10).withMinute(45));
        notifyOnce("notify:admin-assign", accounts.admin(), eventId(order8), null, "订单待派单",
                "订单 #" + order8 + " 已创建 3 小时后开始，尚无有效派单。", "/admin/operations", "ACTION", now.minusHours(1));
        notifyOnce("notify:admin-interrupt", accounts.admin(), eventId(order10), null, "高优先级：服务中断",
                "订单 #" + order10 + " 服务中断，需要协调补服务或异常结案。", "/admin/operations", "HIGH", now.minusDays(1).withHour(10).withMinute(50));
        notifyOnce("notify:admin-dispute", accounts.admin(), eventId(order3), null, "事后争议待处理",
                "订单 #" + order3 + " 用户提交争议，请在争议治理中记录处理结果。", "/admin/operations", "ACTION", now.minusDays(2));
        notifyOnce("notify:family02-dispute", accounts.family02(), eventId(order3), null, "争议已提交",
                "我们已收到您的争议，管理员会在 1 个工作日内核实。", "/user/orders", "NORMAL", now.minusDays(2));

        Long disputeId = registry.recall("family-dispute:zhang");
        if (disputeId != null) {
            notifyOnce("notify:admin-family", accounts.admin(), null, null, "家庭关联争议待协调",
                    "陈德芳档案出现家庭关联争议，请核实双方授权依据。", "/admin/operations", "ACTION", now.minusDays(2));
        }
    }

    private void notifyOnce(String key, long accountId, Long orderEventId, Long reminderId, String title,
                            String body, String targetPath, String priority, LocalDateTime createdAt) {
        if (registry.recall(key) != null) return;
        // 通知表对 (账号, 订单事件) 有唯一约束；seed_key 调整过也应复用已有通知而不是重复插入
        if (orderEventId != null) {
            Long existing = registry.findId("SELECT id FROM notification WHERE account_id=? AND order_event_id=?", accountId, orderEventId);
            if (existing != null) {
                registry.remember(key, "NOTIFICATION", existing);
                return;
            }
        }
        long id = registry.insert(
                "INSERT INTO notification(account_id,order_event_id,reminder_id,title,body,target_path,priority,created_at) VALUES(?,?,?,?,?,?,?,?)",
                accountId, orderEventId, reminderId, title, body, targetPath, priority, createdAt);
        registry.remember(key, "NOTIFICATION", id);
    }

    private Long eventId(Long orderId) {
        if (orderId == null) return null;
        return registry.findId("SELECT id FROM order_event WHERE order_id=? ORDER BY id DESC LIMIT 1", orderId);
    }

    private void seedReminders(Elders elders, Accounts accounts, LocalDateTime now) {
        ensureReminder("reminder:daily-meal", elders.zhang(), accounts.family01(), "每日午餐用药提醒", "DAILY",
                now.plusDays(1).withHour(11).withMinute(30), "ACTIVE");
        ensureReminder("reminder:weekly-clean", elders.liu(), accounts.family02(), "每周保洁前整理物品", "WEEKLY",
                now.plusDays(2).withHour(9).withMinute(0), "ACTIVE");
        ensureReminder("reminder:once-checkup", elders.wang(), accounts.family01(), "复查预约提醒", "ONCE",
                now.plusDays(1).withHour(8).withMinute(0), "ACTIVE");
        ensureReminder("reminder:stopped", elders.zhang(), accounts.family01(), "晚间散步提醒", "DAILY",
                null, "STOPPED");
    }

    private void ensureReminder(String key, long elderId, long creator, String title, String recurrence,
                                LocalDateTime nextFireAt, String status) {
        registry.upsert(key, "REMINDER", () -> registry.insert(
                "INSERT INTO life_reminder(elder_id,created_by,title,recurrence,next_fire_at,status) VALUES(?,?,?,?,?,?)",
                elderId, creator, title, recurrence, nextFireAt, status));
    }

    private void seedAudit(Accounts accounts, Elders elders, LocalDateTime now) {
        String[][] rows = {
                {"WORKER_CREATE", "ACCOUNT", "创建服务人员账号"},
                {"WORKER_PAUSE", "ACCOUNT", "服务人员暂停接单"},
                {"WORKER_EMERGENCY_DISABLE", "ACCOUNT", "紧急停用服务人员账号"},
                {"FAMILY_INVITE", "INVITATION", "邀请家庭成员"},
                {"FAMILY_REVOKE", "ELDER", "撤销家庭成员授权"},
                {"ELDER_IDENTITY_APPROVED", "ELDER", "老人本人身份核验通过"},
                {"LEAVE_DECIDE", "LEAVE", "审批服务人员请假"},
                {"USER_DISABLED", "ACCOUNT", "停用普通用户账号"},
                {"REVIEW_HIDDEN", "REVIEW", "隐藏评价内容"},
                {"FEE_ADJUST_APPROVED", "ORDER", "确认异常费用调整"}
        };
        for (int offset = 0; offset < rows.length; offset++) {
            String[] row = rows[offset];
            String key = "audit:" + offset;
            long targetId = offset % 2 == 0 ? accounts.worker01() : elders.zhang();
            LocalDateTime createdAt = now.minusDays(14 - offset);
            registry.upsert(key, "AUDIT", () -> registry.insert(
                    "INSERT INTO audit_event(actor_id,action,target_type,target_id,summary,created_at) VALUES(?,?,?,?,?,?)",
                    accounts.admin(), row[0], row[1], targetId, row[2], createdAt));
        }
    }

    // ------------------------------------------------------------------ 自检

    private void verifyInvariants() {
        String[][] checks = {
                {"同一订单存在多条有效派单", "SELECT COUNT(*) FROM (SELECT order_id FROM assignment WHERE status IN ('PENDING','ACCEPTED') GROUP BY order_id HAVING COUNT(*)>1) t"},
                {"结算金额与订单应付金额不一致", "SELECT COUNT(*) FROM settlement s JOIN service_order o ON o.id=s.order_id WHERE s.amount<>o.payable_amount"},
                {"非完成订单存在结算", "SELECT COUNT(*) FROM settlement s JOIN service_order o ON o.id=s.order_id WHERE o.status NOT IN ('COMPLETED','ABNORMAL_CLOSED')"},
                {"完成订单缺少结算", "SELECT COUNT(*) FROM service_order o LEFT JOIN settlement s ON s.order_id=o.id WHERE o.status IN ('COMPLETED','ABNORMAL_CLOSED') AND s.id IS NULL"},
                {"已结束订单仍占用人员时间", "SELECT COUNT(*) FROM worker_occupancy w JOIN assignment a ON a.id=w.assignment_id JOIN service_order o ON o.id=a.order_id WHERE w.active=1 AND o.status IN ('COMPLETED','ABNORMAL_CLOSED','CANCELLED','INTERRUPTED')"},
                {"待确认订单缺少已提交履约", "SELECT COUNT(*) FROM service_order o WHERE o.status='WAITING_CONFIRMATION' AND NOT EXISTS (SELECT 1 FROM fulfillment f WHERE f.order_id=o.id AND f.status='SUBMITTED')"},
                {"服务中订单缺少进行中履约", "SELECT COUNT(*) FROM service_order o WHERE o.status='IN_SERVICE' AND NOT EXISTS (SELECT 1 FROM fulfillment f WHERE f.order_id=o.id AND f.status='IN_PROGRESS')"},
                {"同一订单存在多条活跃补服务", "SELECT COUNT(*) FROM (SELECT order_id FROM supplement_plan WHERE status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED') GROUP BY order_id HAVING COUNT(*)>1) t"},
                {"人员时间占用重叠", "SELECT COUNT(*) FROM worker_occupancy a JOIN worker_occupancy b ON a.worker_id=b.worker_id AND a.assignment_id<b.assignment_id AND a.active=1 AND b.active=1 AND a.start_at<b.end_at AND b.start_at<a.end_at"}
        };
        for (String[] check : checks) {
            int violations = registry.count(check[1]);
            if (violations > 0) throw new IllegalStateException("演示数据不满足约束：" + check[0] + "（" + violations + " 条）");
        }
        log.info("[DEMO] 数据一致性自检通过（{} 项）", checks.length);
    }
}
