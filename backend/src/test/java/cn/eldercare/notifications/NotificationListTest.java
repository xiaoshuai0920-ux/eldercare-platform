package cn.eldercare.notifications;

import cn.eldercare.common.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 消息列表在分页字段之外附带未读数，供前端入口角标使用。 */
class NotificationListTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UsernamePasswordAuthenticationToken actor =
            new UsernamePasswordAuthenticationToken("7", "unused", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(notifications))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void listIncludesUnreadCountAlongsideExistingFields() throws Exception {
        Map<String, Object> item = new java.util.HashMap<>();
        item.put("id", 1L);
        item.put("title", "订单 #9 · 上门助餐");
        item.put("read_at", null);
        when(notifications.kindClause(any())).thenReturn("");
        when(notifications.page(anyLong(), anyString(), anyString(), anyInt(), anyInt())).thenReturn(List.of(item));
        when(notifications.count(anyLong(), anyString(), anyString())).thenReturn(25L);
        when(notifications.unreadCount(anyLong(), anyString(), anyString())).thenReturn(3L);

        mvc.perform(get("/api/notifications").principal(actor).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.unread").value(3))
                .andExpect(jsonPath("$.items[0].title").value("订单 #9 · 上门助餐"));
    }
}
