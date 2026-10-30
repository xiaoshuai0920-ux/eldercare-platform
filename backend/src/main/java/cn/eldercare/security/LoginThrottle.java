package cn.eldercare.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.eldercare.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 登录失败限流（按来源 IP，滑动窗口，进程内计数）。
 *
 * <p>账户维度已有 5 次锁定，但攻击者知道用户名就能把别人锁在门外；
 * 这里再按 IP 维度限流，让"拿一个用户名反复试"无法无限放大。
 * 只使用 {@code getRemoteAddr()}，不信任 X-Forwarded-For，避免客户端伪造来源绕过。
 * 单实例内存实现即可满足本机部署；多实例时需换成共享存储。</p>
 */
@Component
public class LoginThrottle {
    private static final int DEFAULT_MAX_FAILURES = 15;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    public LoginThrottle() {
        this(Clock.systemDefaultZone(), DEFAULT_MAX_FAILURES, DEFAULT_WINDOW);
    }

    LoginThrottle(Clock clock, int maxFailures, Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /** 进入登录前调用：窗口内失败次数达到上限直接拒绝，不再校验口令 */
    public void check(HttpServletRequest request) {
        Deque<Instant> queue = failures.get(clientKey(request));
        if (queue == null) return;
        synchronized (queue) {
            prune(queue);
            if (queue.size() >= maxFailures) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "登录尝试过于频繁，请稍后再试");
            }
        }
    }

    public void recordFailure(HttpServletRequest request) {
        Deque<Instant> queue = failures.computeIfAbsent(clientKey(request), key -> new ArrayDeque<>());
        synchronized (queue) {
            prune(queue);
            queue.addLast(clock.instant());
        }
    }

    /** 登录成功后清空该来源的失败记录 */
    public void clear(HttpServletRequest request) {
        failures.remove(clientKey(request));
    }

    /** 供测试与运维观察：某来源当前窗口内的失败次数 */
    public int failureCount(String clientKey) {
        Deque<Instant> queue = failures.get(clientKey);
        if (queue == null) return 0;
        synchronized (queue) {
            prune(queue);
            return queue.size();
        }
    }

    private void prune(Deque<Instant> queue) {
        Instant threshold = clock.instant().minus(window);
        while (!queue.isEmpty() && queue.peekFirst().isBefore(threshold)) {
            queue.removeFirst();
        }
    }

    /** 供审计等场景读取来源 IP：与限流口径一致，只信任 {@code getRemoteAddr()} */
    public static String clientIp(HttpServletRequest request) {
        return clientKey(request);
    }

    private static String clientKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }
}
