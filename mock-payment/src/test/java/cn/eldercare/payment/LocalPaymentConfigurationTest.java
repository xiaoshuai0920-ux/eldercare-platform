package cn.eldercare.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalPaymentConfigurationTest {
    @Test
    void localProfileEnablesConsoleAndResolvesSeparateTokens() throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-values", Map.of(
                "PAYMENT_SERVICE_TOKEN", "service-test", "PAYMENT_CONSOLE_TOKEN", "console-test")));
        environment.getPropertySources().addLast(new YamlPropertySourceLoader()
                .load("local", new ClassPathResource("application-local.yml")).getFirst());

        assertEquals("true", environment.getProperty("spring.web.resources.add-mappings"));
        assertEquals("service-test", environment.getProperty("app.service-token"));
        assertEquals("console-test", environment.getProperty("app.console-token"));
    }
}
