package com.silvertongue.square.controller;

import com.silvertongue.square.dto.CommentVO;
import com.silvertongue.square.dto.PostVO;
import com.silvertongue.square.service.PostService;
import com.silvertongue.support.AbstractWebMvcIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PostControllerIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class PostControllerIntegrationTest extends AbstractWebMvcIntegrationTest {

    @MockBean
    private PostService postService;

    @Test
    void createShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(post("/api/post")
                        .contentType("application/json")
                        .content("""
                                {"content":"hello"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void createShouldValidateBodyAndUseAuthenticatedUser() throws Exception {
        PostVO response = PostVO.builder()
                .id(100L)
                .userId(7L)
                .nickname("alice")
                .content("hello")
                .likeCount(0)
                .createTime(LocalDateTime.now())
                .build();
        when(postService.create(eq(7L), any())).thenReturn(response);

        mockMvc.perform(post("/api/post")
                        .with(bearerToken())
                        .content("""
                                {"content":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("100"))
                .andExpect(jsonPath("$.data.userId").value("7"))
                .andExpect(jsonPath("$.data.content").value("hello"));

        verify(postService).create(eq(7L), any());
    }

    @Test
    void createShouldReturnValidationErrorWhenContentBlank() throws Exception {
        mockMvc.perform(post("/api/post")
                        .with(bearerToken())
                        .content("""
                                {"content":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("content must not be blank"));
    }

    @Test
    void detailShouldExposeNestedCommentPayload() throws Exception {
        CommentVO reply = CommentVO.builder()
                .id(2L)
                .postId(1L)
                .userId(8L)
                .nickname("bob")
                .content("reply")
                .createTime(LocalDateTime.now())
                .build();
        CommentVO topLevel = CommentVO.builder()
                .id(1L)
                .postId(1L)
                .userId(7L)
                .nickname("alice")
                .content("root")
                .replies(List.of(reply))
                .createTime(LocalDateTime.now())
                .build();
        PostVO response = PostVO.builder()
                .id(1L)
                .userId(7L)
                .nickname("alice")
                .content("post")
                .likeCount(2)
                .comments(List.of(topLevel))
                .createTime(LocalDateTime.now())
                .build();
        when(postService.detail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/post/1").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments[0].content").value("root"))
                .andExpect(jsonPath("$.data.comments[0].replies[0].content").value("reply"));
    }

    @Test
    void deleteShouldMapBusinessExceptionToBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("only the author can delete this post"))
                .when(postService).delete(7L, 9L);

        mockMvc.perform(delete("/api/post/9").with(bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("only the author can delete this post"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            PostController.class,
            com.silvertongue.config.JacksonConfig.class,
            com.silvertongue.config.SecurityConfig.class,
            com.silvertongue.common.GlobalExceptionHandler.class,
            com.silvertongue.security.JwtAuthenticationFilter.class
    })
    static class TestApplication {
    }
}
