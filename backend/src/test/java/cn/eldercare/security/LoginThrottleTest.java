package cn.eldercare.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import cn.eldercare.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 登录 IP 限流：达到阈值拒绝、按来源隔离、成功后清零、窗口过后自动恢复。 */
class LoginThrottleTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    private static MockHttpServletRequest from(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksAfterConfiguredFailures() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = new LoginThrottle(clock, 3, WINDOW);
        MockHttpServletRequest request = from("10.0.0.7");

        throttle.recordFailure(request);
        throttle.recordFailure(request);
        throttle.check(request);

        throttle.recordFailure(request);
        ApiException error = assertThrows(ApiException.class, () -> throttle.check(request));
        assertEquals("LOGIN_RATE_LIMITED", error.code());
        assertEquals(3, throttle.failureCount("10.0.0.7"));
    }

    @Test
    void otherAddressesAreNotAffected() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = new LoginThrottle(clock, 2, WINDOW);

        throttle.recordFailure(from("10.0.0.8"));
        throttle.recordFailure(from("10.0.0.8"));

        assertThrows(ApiException.class, () -> throttle.check(from("10.0.0.8")));
        throttle.check(from("10.0.0.9"));
    }

    @Test
    void successfulLoginClearsFailures() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = new LoginThrottle(clock, 2, WINDOW);
        MockHttpServletRequest request = from("10.0.0.10");

        throttle.recordFailure(request);
        throttle.recordFailure(request);
        throttle.clear(request);

        throttle.check(request);
        assertEquals(0, throttle.failureCount("10.0.0.10"));
    }

    @Test
    void windowExpiryRestoresAccess() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = new LoginThrottle(clock, 2, WINDOW);
        MockHttpServletRequest request = from("10.0.0.11");

        throttle.recordFailure(request);
        throttle.recordFailure(request);
        assertThrows(ApiException.class, () -> throttle.check(request));

        clock.advance(WINDOW.plusSeconds(1));
        throttle.check(request);
        assertEquals(0, throttle.failureCount("10.0.0.11"));
    }
}
