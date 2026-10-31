package cn.eldercare.notifications;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.ApiExceptionHandler;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.PermissionColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 停止的提醒可以重新开启，且只有 STOPPED 能从停止态回到进行中。 */
class ReminderResumeTest {
    private static final String BODY = "{\"nextFireAt\":\"2099-05-01T09:00:00\"}";

    private final ReminderRepository reminders = mock(ReminderRepository.class);
    private final FamilyService family = mock(FamilyService.class);
    private final UsernamePasswordAuthenticationToken actor = new UsernamePasswordAuthenticationToken("7", "unused");
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ReminderController(reminders, family))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void stoppedReminderIsResumedWithTheNewFireTime() throws Exception {
        when(reminders.forUpdate(12)).thenReturn(Map.of("elder_id", 4L, "status", "STOPPED"));

        mvc.perform(post("/api/reminders/12/resume").principal(actor)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(family).requirePermission(7, 4, PermissionColumn.MANAGE_REMINDER);
        verify(reminders).resume(12, LocalDateTime.parse("2099-05-01T09:00:00"));
    }

    @Test
    void activeOrFinishedRemindersCannotBeResumed() throws Exception {
        when(reminders.forUpdate(12)).thenReturn(Map.of("elder_id", 4L, "status", "ACTIVE"),
                Map.of("elder_id", 4L, "status", "FINISHED"));

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/reminders/12/resume").principal(actor)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict());
        }
        verify(reminders, never()).resume(anyLong(), any());
    }

    @Test
    void missingReminderReturnsNotFound() throws Exception {
        when(reminders.forUpdate(12)).thenReturn(null);

        mvc.perform(post("/api/reminders/12/resume").principal(actor)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        verify(reminders, never()).resume(anyLong(), any());
    }

    @Test
    void pastFireTimeIsRejected() throws Exception {
        when(reminders.forUpdate(12)).thenReturn(Map.of("elder_id", 4L, "status", "STOPPED"));

        mvc.perform(post("/api/reminders/12/resume").principal(actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nextFireAt\":\"2000-01-01T09:00:00\"}"))
                .andExpect(status().isBadRequest());
        verify(reminders, never()).resume(anyLong(), any());
    }

    @Test
    void unauthorizedFamilyMemberCannotResume() throws Exception {
        when(reminders.forUpdate(12)).thenReturn(Map.of("elder_id", 4L, "status", "STOPPED"));
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权管理提醒"))
                .when(family).requirePermission(7, 4, PermissionColumn.MANAGE_REMINDER);

        mvc.perform(post("/api/reminders/12/resume").principal(actor)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(reminders, never()).resume(anyLong(), any());
    }
}
