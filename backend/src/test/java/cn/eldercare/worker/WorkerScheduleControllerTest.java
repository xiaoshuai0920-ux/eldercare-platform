package cn.eldercare.worker;

import cn.eldercare.common.ApiExceptionHandler;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkerScheduleControllerTest {
    private final WorkerAdminService service = mock(WorkerAdminService.class);
    private final UsernamePasswordAuthenticationToken admin =
            new UsernamePasswordAuthenticationToken("4", "unused");
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkerAdminController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void updatesScheduleUsingWorkerAndScheduleIds() throws Exception {
        mvc.perform(put("/api/admin/workers/3/schedules/8").principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekday\":2,\"startTime\":\"09:00\",\"endTime\":\"13:00\"}"))
                .andExpect(status().isOk());

        verify(service).updateSchedule(4, 3, 8, 2, LocalTime.of(9, 0), LocalTime.of(13, 0));
    }

    @Test
    void deletesScheduleUsingWorkerAndScheduleIds() throws Exception {
        mvc.perform(delete("/api/admin/workers/3/schedules/8").principal(admin))
                .andExpect(status().isNoContent());

        verify(service).deleteSchedule(4, 3, 8);
    }
}
