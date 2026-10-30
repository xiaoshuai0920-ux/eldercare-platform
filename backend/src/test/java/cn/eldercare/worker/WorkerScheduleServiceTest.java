package cn.eldercare.worker;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.AuditService;
import cn.eldercare.identity.AccountMapper;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkerScheduleServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private WorkerAdminService service;

    @BeforeEach
    void setup() {
        service = new WorkerAdminService(jdbc, mock(AccountMapper.class), mock(PasswordEncoder.class), audit);
    }

    @Test
    void rejectsOverlappingUpdateWithoutWriting() {
        when(jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='WORKER' FOR UPDATE", 3L))
                .thenReturn(List.of(Map.<String, Object>of("id", 3L)));
        when(jdbc.queryForList("SELECT id FROM worker_schedule WHERE worker_id=? AND id=? FOR UPDATE", 3L, 8L))
                .thenReturn(List.of(Map.<String, Object>of("id", 8L)));
        when(jdbc.queryForObject(
                "SELECT COUNT(*) FROM worker_schedule WHERE worker_id=? AND weekday=? AND start_time<? AND end_time>? AND id<>?",
                Integer.class, 3L, 2, LocalTime.of(13, 0), LocalTime.of(9, 0), 8L)).thenReturn(1);

        ApiException error = assertThrows(ApiException.class,
                () -> service.updateSchedule(4, 3, 8, 2, LocalTime.of(9, 0), LocalTime.of(13, 0)));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals("SCHEDULE_OVERLAP", error.code());
        verify(jdbc, never()).update(startsWith("UPDATE worker_schedule"), any(), any(), any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void updatesNonOverlappingScheduleAndRecordsAudit() {
        when(jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='WORKER' FOR UPDATE", 3L))
                .thenReturn(List.of(Map.<String, Object>of("id", 3L)));
        when(jdbc.queryForList("SELECT id FROM worker_schedule WHERE worker_id=? AND id=? FOR UPDATE", 3L, 8L))
                .thenReturn(List.of(Map.<String, Object>of("id", 8L)));
        when(jdbc.queryForObject(
                "SELECT COUNT(*) FROM worker_schedule WHERE worker_id=? AND weekday=? AND start_time<? AND end_time>? AND id<>?",
                Integer.class, 3L, 2, LocalTime.of(13, 0), LocalTime.of(9, 0), 8L)).thenReturn(0);

        service.updateSchedule(4, 3, 8, 2, LocalTime.of(9, 0), LocalTime.of(13, 0));

        verify(jdbc).update("UPDATE worker_schedule SET weekday=?,start_time=?,end_time=? WHERE worker_id=? AND id=?",
                2, LocalTime.of(9, 0), LocalTime.of(13, 0), 3L, 8L);
        verify(audit).record(4L, "WORKER_SCHEDULE_UPDATE", "ACCOUNT", 3L,
                "修改排班 #8：周2 09:00-13:00");
    }

    @Test
    void missingScheduleReturnsNotFoundAndDoesNotAudit() {
        when(jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='WORKER' FOR UPDATE", 3L))
                .thenReturn(List.of(Map.<String, Object>of("id", 3L)));
        when(jdbc.update("DELETE FROM worker_schedule WHERE worker_id=? AND id=?", 3L, 8L)).thenReturn(0);

        ApiException error = assertThrows(ApiException.class, () -> service.deleteSchedule(4, 3, 8));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        assertEquals("SCHEDULE_NOT_FOUND", error.code());
        verifyNoInteractions(audit);
    }

    @Test
    void deletesExistingScheduleAndRecordsAudit() {
        when(jdbc.queryForList("SELECT id FROM account WHERE id=? AND role='WORKER' FOR UPDATE", 3L))
                .thenReturn(List.of(Map.<String, Object>of("id", 3L)));
        when(jdbc.update("DELETE FROM worker_schedule WHERE worker_id=? AND id=?", 3L, 8L)).thenReturn(1);

        service.deleteSchedule(4, 3, 8);

        verify(audit).record(4L, "WORKER_SCHEDULE_DELETE", "ACCOUNT", 3L, "删除排班 #8");
    }
}
