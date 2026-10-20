package cn.eldercare.family;

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
class FamilyInvitationTest extends IntegrationTestBase {

    private static final String ALL_PERMISSIONS = """
            {"canViewService":true,"canBook":true,"canManageReminder":true,"canCancel":true,
             "canConfirm":true,"canReview":true,"canConfirmFee":true,"canManageFamily":true}
            """;

    @LocalServerPort
    private int port;

    @Test
    void invitationIsAddressedByUsernameAndRevocationTakesEffect() {
        ApiClient owner = new ApiClient(port);
        assertEquals(201, owner.post("/auth/register",
                "{\"username\":\"owner01\",\"name\":\"Owner\",\"password\":\"123456\"}").status());
        assertEquals(200, owner.post("/auth/login",
                "{\"username\":\"owner01\",\"password\":\"123456\"}").status());

        ApiClient.Result created = owner.post("/elders",
                "{\"fullName\":\"Test Elder\",\"declaresAuthorization\":true}");
        assertEquals(200, created.status(), created.body());
        long elderId = created.json().path("id").asLong();

        ApiClient invitee = new ApiClient(port);
        assertEquals(201, invitee.post("/auth/register",
                "{\"username\":\"invitee01\",\"name\":\"Invitee\",\"password\":\"123456\"}").status());
        assertEquals(200, invitee.post("/auth/login",
                "{\"username\":\"invitee01\",\"password\":\"123456\"}").status());

        ApiClient.Result invited = owner.post("/elders/" + elderId + "/invitations",
                "{\"username\":\"invitee01\",\"permissions\":" + ALL_PERMISSIONS + "}");
        assertEquals(200, invited.status(), invited.body());

        ApiClient.Result pending = invitee.get("/family/invitations");
        assertEquals(200, pending.status(), pending.body());
        assertEquals(1, pending.json().size());
        long invitationId = pending.json().get(0).path("id").asLong();

        assertEquals(200, invitee.post("/family/invitations/" + invitationId + "/accept", null).status());
        assertTrue(invitee.get("/elders").body().contains("\"id\":" + elderId));

        assertEquals(200, owner.post("/elders/" + elderId + "/grants/" + accountId(invitee, elderId) + "/revoke", null).status());
        assertEquals(403, invitee.get("/elders/" + elderId).status());
    }

    @Test
    void invitingUnknownUsernameIsRejected() {
        ApiClient owner = new ApiClient(port);
        assertEquals(201, owner.post("/auth/register",
                "{\"username\":\"owner02\",\"name\":\"Owner\",\"password\":\"123456\"}").status());
        assertEquals(200, owner.post("/auth/login",
                "{\"username\":\"owner02\",\"password\":\"123456\"}").status());
        long elderId = owner.post("/elders",
                "{\"fullName\":\"Second Elder\",\"declaresAuthorization\":true}").json().path("id").asLong();

        ApiClient.Result invited = owner.post("/elders/" + elderId + "/invitations",
                "{\"username\":\"nobody-here\",\"permissions\":" + ALL_PERMISSIONS + "}");
        assertEquals(404, invited.status(), invited.body());
        assertEquals("INVITEE_NOT_FOUND", invited.text("code"));
    }

    private long accountId(ApiClient invitee, long elderId) {
        for (var grant : invitee.get("/elders/" + elderId + "/grants").json()) {
            if ("invitee01".equals(grant.path("username").asText())) return grant.path("account_id").asLong();
        }
        throw new IllegalStateException("未找到受邀账号的授权记录");
    }
}
