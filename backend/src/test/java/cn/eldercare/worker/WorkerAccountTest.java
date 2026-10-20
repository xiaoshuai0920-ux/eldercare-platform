package cn.eldercare.worker;

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
class WorkerAccountTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Test
    void adminCreatesWorkerWithInitialPasswordAndWorkerCanLoginImmediately() {
        ApiClient admin = new ApiClient(port);
        assertEquals(200, admin.post("/auth/login",
                "{\"username\":\"testadmin\",\"password\":\"test-admin-pass\"}").status());

        ApiClient.Result created = admin.post("/admin/workers",
                "{\"username\":\"workerx01\",\"name\":\"Worker X\",\"password\":\"123456\",\"email\":\"\"}");
        assertEquals(200, created.status(), created.body());
        assertTrue(created.json().path("id").asLong() > 0);

        ApiClient worker = new ApiClient(port);
        ApiClient.Result login = worker.post("/auth/login",
                "{\"username\":\"workerx01\",\"password\":\"123456\"}");
        assertEquals(200, login.status(), login.body());
        assertEquals("WORKER", login.text("role"));
        assertEquals(200, worker.get("/worker/dashboard").status());
    }

    @Test
    void duplicateWorkerUsernameIsRejected() {
        ApiClient admin = new ApiClient(port);
        assertEquals(200, admin.post("/auth/login",
                "{\"username\":\"testadmin\",\"password\":\"test-admin-pass\"}").status());
        assertEquals(200, admin.post("/admin/workers",
                "{\"username\":\"workerx02\",\"name\":\"Worker Y\",\"password\":\"123456\"}").status());

        ApiClient.Result duplicate = admin.post("/admin/workers",
                "{\"username\":\"workerx02\",\"name\":\"Worker Y\",\"password\":\"123456\"}");
        assertEquals(409, duplicate.status(), duplicate.body());
        assertEquals("USERNAME_EXISTS", duplicate.text("code"));
    }

    @Test
    void disabledWorkerSessionIsRejected() {
        ApiClient admin = new ApiClient(port);
        assertEquals(200, admin.post("/auth/login",
                "{\"username\":\"testadmin\",\"password\":\"test-admin-pass\"}").status());
        ApiClient.Result created = admin.post("/admin/workers",
                "{\"username\":\"workerx03\",\"name\":\"Worker Z\",\"password\":\"123456\"}");
        assertEquals(200, created.status(), created.body());
        long workerId = created.json().path("id").asLong();

        ApiClient worker = new ApiClient(port);
        assertEquals(200, worker.post("/auth/login",
                "{\"username\":\"workerx03\",\"password\":\"123456\"}").status());
        assertEquals(200, worker.get("/worker/dashboard").status());

        assertEquals(200, admin.post("/admin/workers/" + workerId + "/emergency-disable",
                "{\"reason\":\"演示紧急停用\"}").status());
        assertEquals(401, worker.get("/worker/dashboard").status());
    }
}
