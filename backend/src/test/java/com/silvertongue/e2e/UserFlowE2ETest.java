package com.silvertongue.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserFlowE2ETest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("silvertongue")
            .withUsername("st_user")
            .withPassword("st_pass_2026");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("jwt.secret", () -> "SilverTongue_JWT_Secret_Key_2026_Very_Long_And_Secure");
        registry.add("wechat.app-id", () -> "wx_demo_id");
        registry.add("wechat.app-secret", () -> "wx_demo_secret");
        registry.add("grpc.agent.host", () -> "localhost");
        registry.add("grpc.agent.port", () -> 50051);
        registry.add("spring.data.elasticsearch.uris", () -> "http://localhost:9201");
        registry.add("minio.endpoint", () -> "http://localhost:9000");
        registry.add("minio.access-key", () -> "silvertongue");
        registry.add("minio.secret-key", () -> "silvertongue_minio");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpSchema() throws Exception {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGINT NOT NULL,
                    username VARCHAR(64) NOT NULL,
                    password VARCHAR(128) NOT NULL,
                    nickname VARCHAR(64) NOT NULL,
                    avatar_url VARCHAR(256) NULL,
                    points BIGINT NOT NULL DEFAULT 0,
                    level VARCHAR(20) NOT NULL DEFAULT 'beginner',
                    sign_in_count INT NOT NULL DEFAULT 0,
                    wx_openid VARCHAR(64) NULL,
                    wx_unionid VARCHAR(64) NULL,
                    status TINYINT NOT NULL DEFAULT 0,
                    disabled_time DATETIME NULL,
                    deleted_time DATETIME NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_users_username (username),
                    UNIQUE KEY uk_users_wx_unionid (wx_unionid),
                    KEY idx_users_status (status)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_sign_ins (
                    id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    sign_in_date DATE NOT NULL,
                    points_rewarded INT NOT NULL DEFAULT 0,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_user_signin_date (user_id, sign_in_date),
                    KEY idx_signin_user_date (user_id, sign_in_date)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS points_log (
                    id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    change_amount INT NOT NULL,
                    reason VARCHAR(128) NOT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_points_log_user (user_id, create_time)
                )
                """);

        jdbcTemplate.execute("DELETE FROM points_log");
        jdbcTemplate.execute("DELETE FROM user_sign_ins");
        jdbcTemplate.execute("DELETE FROM users");

        var connection = REDIS.execInContainer("redis-cli", "FLUSHDB");
        assertEquals(0, connection.getExitCode(), "redis FLUSHDB should succeed");
    }

    @Test
    void registerLoginProfileAndSignInShouldWorkAgainstRealMysqlAndRedis() throws Exception {
        String username = "e2e_user_" + System.currentTimeMillis();

        ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                url("/api/user/register"),
                jsonRequest("""
                        {
                          "username": "%s",
                          "password": "secret123",
                          "nickname": "E2E User"
                        }
                        """.formatted(username)),
                String.class
        );
        assertEquals(HttpStatus.OK, registerResponse.getStatusCode());
        JsonNode registerJson = objectMapper.readTree(registerResponse.getBody());
        assertEquals(200, registerJson.path("code").asInt());
        assertEquals(username, registerJson.path("data").path("username").asText());
        assertEquals("E2E User", registerJson.path("data").path("nickname").asText());

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                url("/api/user/login"),
                jsonRequest("""
                        {
                          "username": "%s",
                          "password": "secret123"
                        }
                        """.formatted(username)),
                String.class
        );
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        String token = loginJson.path("data").path("token").asText();
        assertFalse(token.isBlank());
        assertEquals("Bearer", loginJson.path("data").path("tokenType").asText());

        ResponseEntity<String> meResponse = restTemplate.exchange(
                url("/api/user/me"),
                HttpMethod.GET,
                authorizedRequest(token, null),
                String.class
        );
        assertEquals(HttpStatus.OK, meResponse.getStatusCode());
        JsonNode meJson = objectMapper.readTree(meResponse.getBody());
        long userId = meJson.path("data").path("id").asLong();
        assertEquals(username, meJson.path("data").path("username").asText());
        assertEquals(0L, meJson.path("data").path("points").asLong());

        ResponseEntity<String> signInResponse = restTemplate.exchange(
                url("/api/signin"),
                HttpMethod.POST,
                authorizedRequest(token, ""),
                String.class
        );
        assertEquals(HttpStatus.OK, signInResponse.getStatusCode());
        JsonNode signInJson = objectMapper.readTree(signInResponse.getBody());
        int rewarded = signInJson.path("data").path("pointsRewarded").asInt();
        assertTrue(rewarded >= 5 && rewarded <= 15);
        assertEquals(1, signInJson.path("data").path("totalSignInCount").asInt());
        assertEquals(rewarded, signInJson.path("data").path("totalPoints").asInt());

        ResponseEntity<String> calendarResponse = restTemplate.exchange(
                url("/api/signin/calendar?year=%d&month=%d".formatted(LocalDate.now().getYear(), LocalDate.now().getMonthValue())),
                HttpMethod.GET,
                authorizedRequest(token, null),
                String.class
        );
        assertEquals(HttpStatus.OK, calendarResponse.getStatusCode());
        JsonNode calendarJson = objectMapper.readTree(calendarResponse.getBody());
        JsonNode days = calendarJson.path("data");
        assertTrue(days.isArray());
        assertEquals(LocalDate.now().lengthOfMonth(), days.size());
        assertTrue(days.get(LocalDate.now().getDayOfMonth() - 1).path("signed").asBoolean());

        Integer storedPoints = jdbcTemplate.queryForObject("SELECT points FROM users WHERE id = ?", Integer.class, userId);
        Integer signInCount = jdbcTemplate.queryForObject("SELECT sign_in_count FROM users WHERE id = ?", Integer.class, userId);
        Integer signInRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_sign_ins WHERE user_id = ?", Integer.class, userId);
        Integer pointsLogRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM points_log WHERE user_id = ?", Integer.class, userId);

        assertEquals(rewarded, storedPoints);
        assertEquals(1, signInCount);
        assertEquals(1, signInRows);
        assertEquals(1, pointsLogRows);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<String> jsonRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<String> authorizedRequest(String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
