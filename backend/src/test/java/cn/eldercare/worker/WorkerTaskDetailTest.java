package cn.eldercare.worker;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.AuditService;
import cn.eldercare.identity.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 服务人员任务详情：接单前不暴露完整地址与联系电话，接单后返回。 */
class WorkerTaskDetailTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccountMapper accounts = mock(AccountMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    private final WorkerAdminService service = new WorkerAdminService(jdbc, accounts, encoder, audit);

    private Map<String, Object> row(String assignmentStatus) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("order_id", 42L);
        map.put("address_snapshot", "8号楼201");
        map.put("contact_phone", "13800000000");
        map.put("assignment_status", assignmentStatus);
        map.put("elder_name", "陈德远");
        map.put("street_name_snapshot", "示例市 安宁区 南湖街道");
        return map;
    }

    @Test
    void acceptedAssignmentReturnsFullAddressAndPhone() {
        when(jdbc.queryForList(anyString(), eq(42L), eq(7L))).thenReturn(List.of(row("ACCEPTED")));

        Map<String, Object> task = service.task(7, 42);

        assertEquals("8号楼201", task.get("address_snapshot"));
        assertEquals("13800000000", task.get("contact_phone"));
    }

    @Test
    void pendingAssignmentHidesAddressAndPhone() {
        when(jdbc.queryForList(anyString(), eq(42L), eq(7L))).thenReturn(List.of(row("PENDING")));

        Map<String, Object> task = service.task(7, 42);

        assertNull(task.get("address_snapshot"));
        assertNull(task.get("contact_phone"));
        // 街道仍可见，便于接单前判断大致区域
        assertEquals("示例市 安宁区 南湖街道", task.get("street_name_snapshot"));
    }

    @Test
    void taskNotAssignedToWorkerIsForbidden() {
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        ApiException error = assertThrows(ApiException.class, () -> service.task(7, 42));
        assertEquals(HttpStatus.FORBIDDEN, error.status());
    }
}
