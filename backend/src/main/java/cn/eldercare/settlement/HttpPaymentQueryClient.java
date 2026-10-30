package cn.eldercare.settlement;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import cn.eldercare.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class HttpPaymentQueryClient implements PaymentQueryClient {
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public Remote query(String baseUrl, String serviceToken, String attemptRef) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/transactions/" + attemptRef))
                .timeout(Duration.ofSeconds(4))
                .header("X-Service-Token", serviceToken)
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_QUERY_UNAVAILABLE", "模拟支付查询不可用");
        }
        JsonNode body = json.readTree(response.body());
        return new Remote(body.path("status").asText(), body.path("orderRef").asLong(),
                body.path("settlementVersion").asInt(), new BigDecimal(body.path("amount").asText()));
    }
}
