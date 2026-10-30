package cn.eldercare.notifications;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.ApiExceptionHandler;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.PermissionColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReminderUpdateTest {
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
    void activeReminderCanBeUpdatedByAuthorizedFamilyMember() throws Exception {
        when(reminders.forUpdate(12)).thenReturn(Map.of("elder_id", 4L, "status", "ACTIVE"));
        mvc.perform(put("/api/reminders/12").principal(actor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  散步  \",\"recurrence\":\"DAILY\",\"nextFireAt\":\"2099-05-01T09:00:00\"}"))
                .andExpect(status().isOk());
        verify(family).requirePermission(7, 4, PermissionColumn.MANAGE_REMINDER);
        verify(reminders).update(12, "  散步  ", "DAILY", LocalDateTime.parse("2099-05-01T09:00:00"));
    }

    @Test
    void rejectsInvalidInputAndDoesNotWrite() throws Exception {
        when(reminders.forUpdate(12)).thenReturn(Map.of("elder_id", 4L, "status", "ACTIVE"));
        for (String body : new String[] {
                "{\"title\":\" \",\"recurrence\":\"DAILY\",\"nextFireAt\":\"2099-05-01T09:00:00\"}",
                "{\"title\":\"散步\",\"recurrence\":\"MONTHLY\",\"nextFireAt\":\"2099-05-01T09:00:00\"}",
                "{\"title\":\"散步\",\"recurrence\":\"ONCE\",\"nextFireAt\":\"2000-01-01T09:00:00\"}" }) {
            mvc.perform(put("/api/reminders/12").principal(actor).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(reminders, never()).update(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void rejectsStoppedMissingAndUnauthorizedReminders() throws Exception {
        String body = "{\"title\":\"散步\",\"recurrence\":\"ONCE\",\"nextFireAt\":\"2099-05-01T09:00:00\"}";
        when(reminders.forUpdate(12)).thenReturn(null, Map.of("elder_id", 4L, "status", "STOPPED"),
                Map.of("elder_id", 4L, "status", "ACTIVE"));
        mvc.perform(put("/api/reminders/12").principal(actor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/reminders/12").principal(actor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权管理提醒"))
                .when(family).requirePermission(7, 4, PermissionColumn.MANAGE_REMINDER);
        mvc.perform(put("/api/reminders/12").principal(actor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(reminders, never()).update(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void repositoryTrimsTitleAndUpdatesOnlyReminderFields() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var nextFireAt = LocalDateTime.parse("2099-05-01T09:00:00");
        new ReminderRepository(jdbc).update(12, "  散步  ", "DAILY", nextFireAt);
        verify(jdbc).update("UPDATE life_reminder SET title=?,recurrence=?,next_fire_at=? WHERE id=?",
                "散步", "DAILY", nextFireAt, 12L);
    }
}
