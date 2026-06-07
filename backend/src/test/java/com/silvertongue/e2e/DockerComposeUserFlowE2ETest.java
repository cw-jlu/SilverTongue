package com.silvertongue.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DockerComposeUserFlowE2ETest {

    private static final String BASE_URL = System.getProperty("e2e.baseUrl", "http://localhost:8080");
    private static final String MYSQL_URL = System.getProperty(
            "e2e.mysql.url",
            "jdbc:mysql://localhost:3306/silvertongue?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    );
    private static final String MYSQL_USER = System.getProperty("e2e.mysql.user", "st_user");
    private static final String MYSQL_PASSWORD = System.getProperty("e2e.mysql.password", "st_pass_2026");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void verifyComposeServicesAreReachable() throws Exception {
        HttpResponse<String> healthResponse = send("GET", "/actuator/health", null, null);
        assertEquals(200, healthResponse.statusCode(), "backend health endpoint should be reachable");

        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            assertFalse(connection.isClosed(), "mysql connection should be available");
        }
    }

    @Test
    void registerLoginProfileAndSignInShouldWorkAgainstRunningComposeStack() throws Exception {
        String username = "compose_e2e_" + System.currentTimeMillis();

        cleanupUser(username);

        JsonNode registerJson = readJson(send("POST", "/api/user/register", """
                {
                  "username": "%s",
                  "password": "secret123",
                  "nickname": "Compose E2E"
                }
                """.formatted(username), null));
        assertEquals(200, registerJson.path("code").asInt());
        assertEquals(username, registerJson.path("data").path("username").asText());
        assertEquals("Compose E2E", registerJson.path("data").path("nickname").asText());

        JsonNode loginJson = readJson(send("POST", "/api/user/login", """
                {
                  "username": "%s",
                  "password": "secret123"
                }
                """.formatted(username), null));
        assertEquals(200, loginJson.path("code").asInt());
        String token = loginJson.path("data").path("token").asText();
        assertFalse(token.isBlank());
        assertEquals("Bearer", loginJson.path("data").path("tokenType").asText());

        JsonNode meJson = readJson(send("GET", "/api/user/me", null, token));
        assertEquals(200, meJson.path("code").asInt());
        long userId = meJson.path("data").path("id").asLong();
        assertEquals(username, meJson.path("data").path("username").asText());
        assertEquals(0L, meJson.path("data").path("points").asLong());

        JsonNode signInJson = readJson(send("POST", "/api/signin", "", token));
        assertEquals(200, signInJson.path("code").asInt());
        int rewarded = signInJson.path("data").path("pointsRewarded").asInt();
        assertTrue(rewarded >= 5 && rewarded <= 15);
        assertEquals(1, signInJson.path("data").path("totalSignInCount").asInt());
        assertEquals(rewarded, signInJson.path("data").path("totalPoints").asInt());

        LocalDate today = LocalDate.now();
        JsonNode calendarJson = readJson(send(
                "GET",
                "/api/signin/calendar?year=%d&month=%d".formatted(today.getYear(), today.getMonthValue()),
                null,
                token
        ));
        assertEquals(200, calendarJson.path("code").asInt());
        assertEquals(today.lengthOfMonth(), calendarJson.path("data").size());
        assertTrue(calendarJson.path("data").get(today.getDayOfMonth() - 1).path("signed").asBoolean());

        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            assertEquals(rewarded, queryInt(connection, "SELECT points FROM users WHERE id = ?", userId));
            assertEquals(1, queryInt(connection, "SELECT sign_in_count FROM users WHERE id = ?", userId));
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM user_sign_ins WHERE user_id = ?", userId));
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM points_log WHERE user_id = ?", userId));
        }
    }

    private void cleanupUser(String username) throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
             PreparedStatement find = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            find.setString(1, username);
            try (ResultSet rs = find.executeQuery()) {
                while (rs.next()) {
                    long userId = rs.getLong(1);
                    try (PreparedStatement deletePoints = connection.prepareStatement("DELETE FROM points_log WHERE user_id = ?");
                         PreparedStatement deleteSignIns = connection.prepareStatement("DELETE FROM user_sign_ins WHERE user_id = ?");
                         PreparedStatement deleteUser = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                        deletePoints.setLong(1, userId);
                        deletePoints.executeUpdate();
                        deleteSignIns.setLong(1, userId);
                        deleteSignIns.executeUpdate();
                        deleteUser.setLong(1, userId);
                        deleteUser.executeUpdate();
                    }
                }
            }
        }
    }

    private int queryInt(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next(), "query should return a row");
                return rs.getInt(1);
            }
        }
    }

    private JsonNode readJson(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), "request should succeed: " + response.body());
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
