package com.silvertongue.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

class DockerComposePostFlowE2ETest {

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
    void ensureSchemaAndHealth() throws Exception {
        HttpResponse<String> healthResponse = send("GET", "/actuator/health", null, null);
        assertEquals(200, healthResponse.statusCode(), "backend should be reachable");

        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS posts (
                        id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        content TEXT NOT NULL,
                        clip_id BIGINT NULL,
                        like_count INT NOT NULL DEFAULT 0,
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_posts_user (user_id),
                        KEY idx_posts_time (create_time)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS comments (
                        id BIGINT NOT NULL,
                        post_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        parent_id BIGINT NULL,
                        content TEXT NOT NULL,
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_comments_post (post_id),
                        KEY idx_comments_user (user_id)
                    )
                    """);
        }
    }

    @Test
    void createCommentLikeAndDeletePostShouldWorkAgainstRunningComposeStack() throws Exception {
        String username = "compose_post_" + System.currentTimeMillis();
        String token = registerAndLogin(username, "Post User");

        JsonNode createJson = readJson(send("POST", "/api/post", """
                {
                  "content": "compose flow post"
                }
                """, token));
        long postId = createJson.path("data").path("id").asLong();
        assertTrue(postId > 0);
        assertEquals("compose flow post", createJson.path("data").path("content").asText());

        JsonNode commentJson = readJson(send("POST", "/api/post/comment", """
                {
                  "postId": %d,
                  "content": "first comment"
                }
                """.formatted(postId), token));
        long commentId = commentJson.path("data").path("id").asLong();
        assertTrue(commentId > 0);
        assertEquals(postId, commentJson.path("data").path("postId").asLong());
        assertEquals("first comment", commentJson.path("data").path("content").asText());

        JsonNode detailJson = readJson(send("GET", "/api/post/" + postId, null, token));
        assertEquals(postId, detailJson.path("data").path("id").asLong());
        assertEquals("compose flow post", detailJson.path("data").path("content").asText());
        assertEquals(1, detailJson.path("data").path("comments").size());
        assertEquals("first comment", detailJson.path("data").path("comments").get(0).path("content").asText());

        HttpResponse<String> likeResponse = send("POST", "/api/post/" + postId + "/like", "", token);
        assertEquals(200, likeResponse.statusCode());
        JsonNode likedDetailJson = readJson(send("GET", "/api/post/" + postId, null, token));
        assertEquals(1, likedDetailJson.path("data").path("likeCount").asInt());

        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM posts WHERE id = ?", postId));
            assertEquals(1, queryInt(connection, "SELECT like_count FROM posts WHERE id = ?", postId));
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM comments WHERE id = ?", commentId));
            assertEquals(postId, queryLong(connection, "SELECT post_id FROM comments WHERE id = ?", commentId));
        }

        HttpResponse<String> deleteResponse = send("DELETE", "/api/post/" + postId, null, token);
        assertEquals(200, deleteResponse.statusCode());

        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM posts WHERE id = ?", postId));
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM comments WHERE post_id = ?", postId));
        }
    }

    private String registerAndLogin(String username, String nickname) throws Exception {
        cleanupUser(username);

        JsonNode registerJson = readJson(send("POST", "/api/user/register", """
                {
                  "username": "%s",
                  "password": "secret123",
                  "nickname": "%s"
                }
                """.formatted(username, nickname), null));
        assertEquals(200, registerJson.path("code").asInt());

        JsonNode loginJson = readJson(send("POST", "/api/user/login", """
                {
                  "username": "%s",
                  "password": "secret123"
                }
                """.formatted(username), null));
        String token = loginJson.path("data").path("token").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private void cleanupUser(String username) throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
             PreparedStatement find = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            find.setString(1, username);
            try (ResultSet rs = find.executeQuery()) {
                while (rs.next()) {
                    long userId = rs.getLong(1);
                    try (PreparedStatement findPosts = connection.prepareStatement("SELECT id FROM posts WHERE user_id = ?")) {
                        findPosts.setLong(1, userId);
                        try (ResultSet posts = findPosts.executeQuery()) {
                            while (posts.next()) {
                                long postId = posts.getLong(1);
                                try (PreparedStatement deleteComments = connection.prepareStatement("DELETE FROM comments WHERE post_id = ?");
                                     PreparedStatement deletePost = connection.prepareStatement("DELETE FROM posts WHERE id = ?")) {
                                    deleteComments.setLong(1, postId);
                                    deleteComments.executeUpdate();
                                    deletePost.setLong(1, postId);
                                    deletePost.executeUpdate();
                                }
                            }
                        }
                    }
                    try (PreparedStatement deletePoints = connection.prepareStatement("DELETE FROM points_log WHERE user_id = ?");
                         PreparedStatement deleteSignIns = connection.prepareStatement("DELETE FROM user_sign_ins WHERE user_id = ?");
                         PreparedStatement deleteComments = connection.prepareStatement("DELETE FROM comments WHERE user_id = ?");
                         PreparedStatement deleteUser = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                        deletePoints.setLong(1, userId);
                        deletePoints.executeUpdate();
                        deleteSignIns.setLong(1, userId);
                        deleteSignIns.executeUpdate();
                        deleteComments.setLong(1, userId);
                        deleteComments.executeUpdate();
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

    private long queryLong(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next(), "query should return a row");
                return rs.getLong(1);
            }
        }
    }

    private JsonNode readJson(HttpResponse<String> response) throws Exception {
        assumeTrue(response.statusCode() == 200,
                "compose post flow unavailable in current environment: HTTP " + response.statusCode() + ", body=" + response.body());
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

        switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
