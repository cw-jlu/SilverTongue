package com.silvertongue.support;

import com.silvertongue.security.JwtAuthenticationFilter;
import com.silvertongue.user.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

public abstract class AbstractWebMvcIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockBean
    protected JwtService jwtService;

    @Mock
    protected Claims claims;

    @BeforeEach
    void stubJwtDefaults() {
        lenient().when(jwtService.parseToken("valid-token")).thenReturn(claims);
        lenient().when(jwtService.getUserId(claims)).thenReturn(7L);
        lenient().when(jwtService.getUsername(claims)).thenReturn("alice");
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected org.springframework.test.web.servlet.request.RequestPostProcessor bearerToken() {
        return request -> {
            request.addHeader("Authorization", "Bearer valid-token");
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            return request;
        };
    }
}
