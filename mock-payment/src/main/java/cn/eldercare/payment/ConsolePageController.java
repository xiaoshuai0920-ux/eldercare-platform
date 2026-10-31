package cn.eldercare.payment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地模拟支付控制台页面。
 *
 * <p>把 {@code app.console-token} 注入静态页面，避免演示时手动抄写令牌导致 401。
 * 只在 {@code local} profile 注册，且服务固定绑定 127.0.0.1，令牌不会进入 prod 环境，
 * 也不会写进任何可下载的 JS 或配置文件。</p>
 */
@RestController
@Profile("local")
public class ConsolePageController {
    private static final Logger log = LoggerFactory.getLogger(ConsolePageController.class);
    private static final String PLACEHOLDER = "__CONSOLE_TOKEN__";
    /** 令牌只接受常规字符，避免拼进 HTML 属性时被截断或注入 */
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{8,128}");

    private final String consoleToken;
    private final Resource page;

    public ConsolePageController(@Value("${app.console-token:}") String consoleToken,
                                 @Value("classpath:static/console.html") Resource page) {
        this.consoleToken = consoleToken;
        this.page = page;
    }

    @GetMapping(value = "/console.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> console() throws IOException {
        String html;
        try (InputStream input = page.getInputStream()) {
            html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String token = consoleToken == null ? "" : consoleToken.trim();
        if (!SAFE_TOKEN.matcher(token).matches()) {
            log.warn("PAYMENT_CONSOLE_TOKEN 未配置或格式异常，控制台令牌留空，需要手动填写");
            token = "";
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .body(html.replace(PLACEHOLDER, token));
    }
}
