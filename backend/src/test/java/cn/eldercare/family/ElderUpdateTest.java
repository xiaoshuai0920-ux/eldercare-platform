package cn.eldercare.family;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.ApiExceptionHandler;
import cn.eldercare.common.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ElderUpdateTest {
    @Test
    void updateUsesActiveFamilyManagementGrantAndRecordsAudit() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), eq(7L), eq(4L)))
                .thenReturn(List.of(1L));
        var birthDate = LocalDate.of(1948, 2, 3);

        var result = new FamilyService(jdbc, audit).updateElder(7, 4, "  林阿姨  ", birthDate);

        assertEquals(new FamilyService.ElderView(4L, "林阿姨", birthDate), result);
        verify(jdbc).query(contains("g.can_manage_family=1"), ArgumentMatchers.<RowMapper<Long>>any(), eq(7L), eq(4L));
        verify(jdbc).update("UPDATE elder_profile SET full_name=?,birth_date=? WHERE id=? AND status='ACTIVE'",
                "林阿姨", birthDate, 4L);
        verify(audit).record(7L, "ELDER_UPDATE", "ELDER", 4L, "修改老人档案");
    }

    @Test
    void revokedOrMissingGrantCannotModifyTheProfile() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), eq(7L), eq(4L)))
                .thenReturn(List.of());

        ApiException error = assertThrows(ApiException.class,
                () -> new FamilyService(jdbc, audit).updateElder(7, 4, "林阿姨", null));
        assertEquals("FAMILY_PERMISSION_REQUIRED", error.code());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void endpointValidatesNameAndBirthDateAndPreservesOptionalDate() throws Exception {
        FamilyService service = mock(FamilyService.class);
        var actor = new UsernamePasswordAuthenticationToken("7", "unused");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FamilyController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        when(service.updateElder(7, 4, "林阿姨", null))
                .thenReturn(new FamilyService.ElderView(4L, "林阿姨", null));

        mvc.perform(put("/api/elders/4").principal(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"林阿姨\",\"birthDate\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("林阿姨"));
        for (String body : List.of("{\"fullName\":\"   \"}",
                "{\"fullName\":\"" + "a".repeat(81) + "\"}",
                "{\"fullName\":\"林阿姨\",\"birthDate\":\"not-a-date\"}")) {
            mvc.perform(put("/api/elders/4").principal(actor).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, times(1)).updateElder(anyLong(), anyLong(), anyString(), any());
    }
}
