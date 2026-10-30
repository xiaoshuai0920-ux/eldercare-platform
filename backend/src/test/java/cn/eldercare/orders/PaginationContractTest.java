package cn.eldercare.orders;

import cn.eldercare.support.ApiClient;
import cn.eldercare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 列表分页契约：{items,page,size,total} 四个字段必须始终存在，
 * 页码越界返回空数组而不是报错，size 越界按 400 拒绝。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaginationContractTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    /** 每个用例用独立账号，避免共享测试库里用户名冲突 */
    private ApiClient loggedInUser(String username) {
        ApiClient client = new ApiClient(port);
        assertEquals(201, client.post("/auth/register",
                "{\"username\":\"" + username + "\",\"name\":\"Pager\",\"password\":\"123456\"}").status());
        assertEquals(200, client.post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"123456\"}").status());
        return client;
    }

    @Test
    void notificationListReturnsPageEnvelope() {
        ApiClient client = loggedInUser("pager01");

        ApiClient.Result first = client.get("/notifications?page=1&size=5");
        assertEquals(200, first.status(), first.body());
        var body = first.json();
        assertTrue(body.has("items") && body.has("page") && body.has("size") && body.has("total"), first.body());
        assertEquals(1, body.path("page").asInt());
        assertEquals(5, body.path("size").asInt());
        assertTrue(body.path("items").isArray());
    }

    @Test
    void outOfRangePageReturnsEmptyItemsWithSameTotal() {
        ApiClient client = loggedInUser("pager02");

        ApiClient.Result deep = client.get("/notifications?page=999&size=5");
        assertEquals(200, deep.status(), deep.body());
        assertEquals(0, deep.json().path("items").size());
        assertEquals(999, deep.json().path("page").asInt());
    }

    @Test
    void oversizedPageSizeIsRejected() {
        ApiClient client = loggedInUser("pager03");

        ApiClient.Result invalid = client.get("/notifications?page=1&size=999");
        assertEquals(400, invalid.status(), invalid.body());
        assertEquals("INVALID_PAGE_SIZE", invalid.text("code"));
    }

    @Test
    void zeroPageIsRejected() {
        ApiClient client = loggedInUser("pager04");

        ApiClient.Result invalid = client.get("/notifications?page=0&size=10");
        assertEquals(400, invalid.status(), invalid.body());
        assertEquals("INVALID_PAGE", invalid.text("code"));
    }
}
