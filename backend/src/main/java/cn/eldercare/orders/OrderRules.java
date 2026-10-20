package cn.eldercare.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;

import cn.eldercare.common.ApiException;
import org.springframework.http.HttpStatus;

public final class OrderRules {
    private OrderRules() { }

    public static BigDecimal quote(String pricingModel, BigDecimal unitPrice, int durationMinutes,
                                   int minMinutes, int maxMinutes, LocalTime availableFrom, LocalTime availableTo,
                                   int advanceMinutes, LocalDateTime startAt, LocalDateTime now) {
        LocalDateTime endAt = startAt.plusMinutes(durationMinutes);
        if (durationMinutes < minMinutes || durationMinutes > maxMinutes || !startAt.toLocalDate().equals(endAt.toLocalDate())
                || startAt.isBefore(now.plusMinutes(advanceMinutes))
                || startAt.toLocalTime().isBefore(availableFrom) || endAt.toLocalTime().isAfter(availableTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKING_RULE_VIOLATION", "预约时间或时长不符合服务规则");
        }
        if (unitPrice.signum() < 0) throw new IllegalArgumentException("服务价格不能为负数");
        if ("FIXED".equals(pricingModel)) return unitPrice.setScale(2, RoundingMode.HALF_UP);
        if ("HOURLY".equals(pricingModel)) return unitPrice.multiply(BigDecimal.valueOf(durationMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        throw new IllegalArgumentException("未知计价方式");
    }
}
