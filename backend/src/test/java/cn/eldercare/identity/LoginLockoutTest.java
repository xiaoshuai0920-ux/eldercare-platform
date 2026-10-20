package cn.eldercare.identity;

import cn.eldercare.support.ApiClient;
import cn.eldercare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoginLockoutTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Test
    void locksAccountAfterFiveFailedAttempts() {
        ApiClient client = new ApiClient(port);
        assertEquals(201, client.post("/auth/register",
                "{\"username\":\"lockout01\",\"name\":\"Lock\",\"password\":\"123456\"}").status());

        for (int attempt = 1; attempt <= 5; attempt++) {
            ApiClient.Result failed = client.post("/auth/login",
                    "{\"username\":\"lockout01\",\"password\":\"wrong-pass\"}");
            assertEquals(401, failed.status(), "第 " + attempt + " 次失败应为 401");
        }

        ApiClient.Result locked = client.post("/auth/login",
                "{\"username\":\"lockout01\",\"password\":\"123456\"}");
        assertEquals(429, locked.status(), locked.body());
        assertEquals("ACCOUNT_LOCKED", locked.text("code"));
    }
}
