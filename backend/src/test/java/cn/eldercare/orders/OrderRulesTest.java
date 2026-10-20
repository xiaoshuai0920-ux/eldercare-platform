package cn.eldercare.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import cn.eldercare.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderRulesTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 0);

    @Test
    void hourlyPriceUsesDecimalArithmetic() {
        BigDecimal amount = OrderRules.quote("HOURLY", new BigDecimal("89.99"), 90, 60, 180,
                LocalTime.of(8, 0), LocalTime.of(20, 0), 120, now.plusDays(1), now);
        assertEquals(new BigDecimal("134.99"), amount);
    }

    @Test
    void bookingCannotCrossDayOrIgnoreAdvanceTime() {
        assertThrows(ApiException.class, () -> OrderRules.quote("FIXED", new BigDecimal("120.00"), 90, 30, 180,
                LocalTime.of(8, 0), LocalTime.of(23, 59), 120, now.withHour(23).withMinute(30), now));
        assertThrows(ApiException.class, () -> OrderRules.quote("FIXED", new BigDecimal("120.00"), 60, 30, 180,
                LocalTime.of(8, 0), LocalTime.of(20, 0), 120, now.plusMinutes(30), now));
    }
}
