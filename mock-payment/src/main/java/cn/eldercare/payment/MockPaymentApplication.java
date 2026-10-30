package cn.eldercare.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.Set;

@SpringBootApplication
public class MockPaymentApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MockPaymentApplication.class);
        application.addInitializers(context -> requireProfile(context.getEnvironment()));
        application.run(args);
    }

    private static void requireProfile(ConfigurableEnvironment environment) {
        Set<String> supported = Set.of("local", "prod", "test");
        boolean selected = Arrays.stream(environment.getActiveProfiles()).anyMatch(supported::contains);
        if (!selected) throw new IllegalStateException("必须显式指定 Spring Profile：local、prod 或 test。");
    }
}
