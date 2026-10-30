package cn.eldercare.orders;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserOrderPaginationTest {
    @Test
    void countsAllAuthorizedOrdersAndFiltersBeforePaging() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrderService service = new OrderService(jdbc, mock(FamilyService.class), mock(OrderActionAccess.class));
        when(jdbc.queryForList(contains("GROUP BY o.status"), eq(7L)))
                .thenReturn(List.of(Map.of("status", "WAITING_ASSIGNMENT", "total", 65L),
                        Map.of("status", "WAITING_CONFIRMATION", "total", 2L),
                        Map.of("status", "COMPLETED", "total", 3L)));
        when(jdbc.queryForList(contains("o.status IN (?,?,?)"), eq(7L), eq("WAITING_ASSIGNMENT"),
                eq("WAITING_ACCEPTANCE"), eq("WAITING_SERVICE"), eq(10), eq(10L)))
                .thenReturn(List.of(Map.of("id", 20L, "status", "WAITING_ASSIGNMENT")));

        var result = service.userOrders(7, 2, 10, "upcoming");
        assertEquals(65, result.total());
        assertEquals(70, result.counts().total());
        assertEquals(2, result.counts().pending());
        assertEquals(65, result.counts().upcoming());
        assertEquals(3, result.counts().done());
        assertEquals(20L, result.items().getFirst().get("id"));
        verify(jdbc).queryForList(contains("g.account_id=? AND g.status='ACTIVE' AND g.can_view_service=1"),
                eq(7L), eq("WAITING_ASSIGNMENT"), eq("WAITING_ACCEPTANCE"), eq("WAITING_SERVICE"), eq(10), eq(10L));
    }

    @Test
    void rejectsUnknownFilterWithoutQueryingDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrderService service = new OrderService(jdbc, mock(FamilyService.class), mock(OrderActionAccess.class));
        assertEquals("INVALID_ORDER_GROUP", assertThrows(ApiException.class,
                () -> service.userOrders(7, 1, 10, "anything")).code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void noGroupKeepsOriginalAllOrdersContractAndRejectsInvalidPaging() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrderService service = new OrderService(jdbc, mock(FamilyService.class), mock(OrderActionAccess.class));
        when(jdbc.queryForList(contains("GROUP BY o.status"), eq(7L)))
                .thenReturn(List.of(Map.of("status", "WAITING_ASSIGNMENT", "total", 51L)));
        when(jdbc.queryForList(contains("ORDER BY o.id DESC LIMIT ? OFFSET ?"), eq(7L), eq(10), eq(0L)))
                .thenReturn(List.of(Map.of("id", 51L)));

        var result = service.userOrders(7, 1, 10, null);
        assertEquals(51, result.total());
        assertEquals(51, result.counts().total());
        assertEquals(51L, result.items().getFirst().get("id"));
        assertEquals("INVALID_PAGE", assertThrows(ApiException.class,
                () -> service.userOrders(7, 0, 10, null)).code());
        assertEquals("INVALID_PAGE_SIZE", assertThrows(ApiException.class,
                () -> service.userOrders(7, 1, 101, null)).code());
    }
}
