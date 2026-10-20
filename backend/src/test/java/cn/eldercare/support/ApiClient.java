package cn.eldercare.support;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 轻量 HTTP 客户端：保持 JSESSIONID Cookie，写请求自动附带 CSRF 头。
 * 使用真实 Servlet 容器端口，覆盖 MockMvc 无法模拟的会话固定防护与 Cookie 行为。
 */
public class ApiClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String base;
    private String csrfToken;

    public ApiClient(int port) {
        this.base = "http://127.0.0.1:" + port + "/api";
        this.http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Result get(String path) {
        return send("GET", path, null);
    }

    public Result post(String path, String body) {
        return send("POST", path, body == null ? "{}" : body);
    }

    public void forgetCsrf() {
        csrfToken = null;
    }

    private Result send(String method, String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                    .header("Accept", "application/json");
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json;charset=UTF-8");
                builder.header("X-CSRF-TOKEN", csrf());
                builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Result(response.statusCode(), response.body());
        } catch (Exception error) {
            throw new IllegalStateException(method + " " + path + " 调用失败", error);
        }
    }

    private String csrf() {
        if (csrfToken == null) {
            Result result = get("/auth/csrf");
            if (result.status() != 200) throw new IllegalStateException("获取 CSRF 令牌失败：" + result.status());
            try {
                csrfToken = JSON.readTree(result.body()).path("token").asText();
            } catch (Exception error) {
                throw new IllegalStateException("解析 CSRF 响应失败", error);
            }
        }
        return csrfToken;
    }

    public record Result(int status, String body) {
        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (Exception error) {
                throw new IllegalStateException("响应不是合法 JSON：" + body, error);
            }
        }

        public String text(String field) {
            return json().path(field).asText();
        }
    }
}
