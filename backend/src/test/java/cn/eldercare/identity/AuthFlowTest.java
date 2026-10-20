package cn.eldercare.identity;

import cn.eldercare.support.ApiClient;
import cn.eldercare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Test
    void registerThenLoginByUsername() {
        ApiClient client = new ApiClient(port);

        ApiClient.Result registered = client.post("/auth/register",
                "{\"username\":\"tester01\",\"name\":\"Tester\",\"password\":\"123456\"}");
        assertEquals(201, registered.status(), registered.body());

        ApiClient.Result login = client.post("/auth/login",
                "{\"username\":\"tester01\",\"password\":\"123456\"}");
        assertEquals(200, login.status(), login.body());
        assertEquals("tester01", login.text("username"));
        assertEquals("USER", login.text("role"));

        ApiClient.Result me = client.get("/auth/me");
        assertEquals(200, me.status(), me.body());
        assertEquals("tester01", me.text("username"));

        assertEquals(204, client.post("/auth/logout", null).status());
        assertEquals(401, client.get("/auth/me").status());
    }

    @Test
    void duplicateUsernameIsRejected() {
        ApiClient client = new ApiClient(port);
        assertEquals(201, client.post("/auth/register",
                "{\"username\":\"tester02\",\"name\":\"Tester\",\"password\":\"123456\"}").status());

        ApiClient.Result duplicate = client.post("/auth/register",
                "{\"username\":\"tester02\",\"name\":\"Tester\",\"password\":\"123456\"}");
        assertEquals(409, duplicate.status(), duplicate.body());
        assertEquals("USERNAME_EXISTS", duplicate.text("code"));
    }

    @Test
    void weakPasswordAndIllegalUsernameAreRejected() {
        ApiClient client = new ApiClient(port);

        ApiClient.Result shortPassword = client.post("/auth/register",
                "{\"username\":\"tester03\",\"name\":\"Tester\",\"password\":\"12345\"}");
        assertEquals(400, shortPassword.status(), shortPassword.body());
        assertEquals("WEAK_PASSWORD", shortPassword.text("code"));

        ApiClient.Result badUsername = client.post("/auth/register",
                "{\"username\":\"a\",\"name\":\"Tester\",\"password\":\"123456\"}");
        assertEquals(400, badUsername.status(), badUsername.body());
    }

    @Test
    void legacyEmailVerificationEndpointIsNoLongerAvailable() {
        ApiClient client = new ApiClient(port);
        int status = client.post("/auth/verify-email", "{\"email\":\"a@b.com\"}").status();
        assertTrue(status == 401 || status == 404, "旧验证码接口应不可用，实际 " + status);
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        ApiClient client = new ApiClient(port);
        assertEquals(201, client.post("/auth/register",
                "{\"username\":\"tester04\",\"name\":\"Tester\",\"password\":\"123456\"}").status());
        assertEquals(200, client.post("/auth/login",
                "{\"username\":\"tester04\",\"password\":\"123456\"}").status());

        ApiClient.Result wrong = client.post("/auth/password",
                "{\"currentPassword\":\"bad-pass\",\"newPassword\":\"abcdef\"}");
        assertEquals(400, wrong.status(), wrong.body());
        assertEquals("CURRENT_PASSWORD_INVALID", wrong.text("code"));

        assertEquals(204, client.post("/auth/password",
                "{\"currentPassword\":\"123456\",\"newPassword\":\"abcdef\"}").status());
        ApiClient fresh = new ApiClient(port);
        assertEquals(200, fresh.post("/auth/login",
                "{\"username\":\"tester04\",\"password\":\"abcdef\"}").status());
    }
}
