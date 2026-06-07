package com.silvertongue.user.controller;

import com.silvertongue.support.AbstractWebMvcIntegrationTest;
import com.silvertongue.user.dto.CalendarDay;
import com.silvertongue.user.dto.SignInResponse;
import com.silvertongue.user.service.SignInService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SignInControllerIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class SignInControllerIntegrationTest extends AbstractWebMvcIntegrationTest {

    @MockBean
    private SignInService signInService;

    @Test
    void signInShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/signin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void signInShouldReturnWrappedPayloadForAuthenticatedUser() throws Exception {
        SignInResponse response = SignInResponse.builder()
                .pointsRewarded(10)
                .totalSignInCount(5)
                .totalPoints(80L)
                .build();
        when(signInService.signIn(7L)).thenReturn(response);

        mockMvc.perform(post("/api/signin").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pointsRewarded").value(10))
                .andExpect(jsonPath("$.data.totalSignInCount").value(5))
                .andExpect(jsonPath("$.data.totalPoints").value(80));

        verify(signInService).signIn(7L);
    }

    @Test
    void calendarShouldPassAuthenticatedUserAndQueryParams() throws Exception {
        when(signInService.calendar(7L, 2026, 6))
                .thenReturn(List.of(new CalendarDay(1, true), new CalendarDay(2, false)));

        mockMvc.perform(get("/api/signin/calendar")
                        .queryParam("year", "2026")
                        .queryParam("month", "6")
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].day").value(1))
                .andExpect(jsonPath("$.data[0].signed").value(true))
                .andExpect(jsonPath("$.data[1].signed").value(false));

        verify(signInService).calendar(7L, 2026, 6);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            SignInController.class,
            com.silvertongue.config.SecurityConfig.class,
            com.silvertongue.common.GlobalExceptionHandler.class,
            com.silvertongue.security.JwtAuthenticationFilter.class
    })
    static class TestApplication {
    }
}
