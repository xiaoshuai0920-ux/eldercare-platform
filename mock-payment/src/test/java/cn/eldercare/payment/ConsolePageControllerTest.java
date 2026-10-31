package cn.eldercare.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ConsolePageControllerTest {
    private final ClassPathResource page = new ClassPathResource("static/console.html");

    @Test
    void injectsConfiguredTokenAndRemovesPlaceholder() throws Exception {
        String body = controller("console-test-token").console().getBody();

        assertTrue(body.contains("value=\"console-test-token\""), "令牌应写入页面");
        assertFalse(body.contains("__CONSOLE_TOKEN__"), "占位符不应残留");
    }

    @Test
    void leavesTokenFieldEmptyWhenNotConfigured() throws Exception {
        String body = controller("").console().getBody();

        assertFalse(body.contains("__CONSOLE_TOKEN__"));
        assertTrue(body.contains("value=\"\""), "未配置时令牌框应为空，由页面提示手动填写");
    }

    @Test
    void refusesTokenThatWouldBreakHtmlAttribute() throws Exception {
        String body = controller("bad\"onload=alert(1)").console().getBody();

        assertFalse(body.contains("bad\"onload"), "非法字符不应写入页面属性");
        assertFalse(body.contains("__CONSOLE_TOKEN__"));
    }

    @Test
    void servesHtmlAsUtf8WithoutCaching() throws Exception {
        var response = controller("console-test-token").console();

        assertEquals("text/html;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"), "令牌页面不应被缓存");
    }

    private ConsolePageController controller(String token) {
        return new ConsolePageController(token, page);
    }
}
