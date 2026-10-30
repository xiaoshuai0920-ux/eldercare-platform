package cn.eldercare.settlement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalPaymentConfigurationTest {
    @Test
    void localProfileEnablesMockPaymentAndResolvesServiceToken() throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "test-values", java.util.Map.of("PAYMENT_SERVICE_TOKEN", "test-token")));
        environment.getPropertySources().addLast(new YamlPropertySourceLoader()
                .load("local", new ClassPathResource("application-local.yml")).getFirst());

        assertEquals("true", environment.getProperty("app.mock-payment.enabled"));
        assertEquals("http://127.0.0.1:8081", environment.getProperty("app.payment-base-url"));
        assertEquals("test-token", environment.getProperty("app.payment-service-token"));
    }
}
