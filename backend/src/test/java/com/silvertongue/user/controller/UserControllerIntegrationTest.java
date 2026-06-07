package com.silvertongue.user.controller;

import com.silvertongue.support.AbstractWebMvcIntegrationTest;
import com.silvertongue.user.dto.LoginResponse;
import com.silvertongue.user.dto.RegisterRequest;
import com.silvertongue.user.dto.UserProfileResponse;
import com.silvertongue.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UserControllerIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class UserControllerIntegrationTest extends AbstractWebMvcIntegrationTest {

    @MockBean
    private UserService userService;

    @Test
    void registerShouldAllowAnonymousRequestAndWrapSuccessBody() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(1L)
                .username("alice")
                .nickname("Alice")
                .points(0L)
                .level("beginner")
                .signInCount(0)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();
        when(userService.register(any())).thenReturn(profile);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret123");
        request.setNickname("Alice");

        mockMvc.perform(post("/api/user/register")
                        .contentType("application/json")
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.nickname").value("Alice"));
    }

    @Test
    void registerShouldReturnValidationErrorForShortUsername() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab");
        request.setPassword("secret123");

        mockMvc.perform(post("/api/user/register")
                        .contentType("application/json")
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("username length must be between 3 and 64"));
    }

    @Test
    void meShouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @Test
    void meShouldReadAuthenticatedPrincipalFromJwtFilter() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(7L)
                .username("alice")
                .nickname("Alice")
                .points(15L)
                .level("B1")
                .signInCount(2)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();
        when(userService.getProfile(7L)).thenReturn(profile);

        mockMvc.perform(get("/api/user/me").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("7"))
                .andExpect(jsonPath("$.data.username").value("alice"));

        verify(userService).getProfile(7L);
    }

    @Test
    void loginShouldConvertServiceExceptionToBadRequest() throws Exception {
        when(userService.login(any())).thenThrow(new IllegalArgumentException("invalid username or password"));

        String body = """
                {"username":"alice","password":"wrong"}
                """;

        mockMvc.perform(post("/api/user/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("invalid username or password"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            UserController.class,
            com.silvertongue.config.JacksonConfig.class,
            com.silvertongue.config.SecurityConfig.class,
            com.silvertongue.common.GlobalExceptionHandler.class,
            com.silvertongue.security.JwtAuthenticationFilter.class
    })
    static class TestApplication {
    }
}
