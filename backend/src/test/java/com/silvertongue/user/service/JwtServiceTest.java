package com.silvertongue.user.service;

import com.silvertongue.user.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void generateTokenAndParseTokenShouldRoundTripUserClaims() {
        JwtService jwtService = new JwtService(SECRET, 60_000L);
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");

        String token = jwtService.generateToken(user);
        Claims claims = jwtService.parseToken(token);

        assertNotNull(claims);
        assertEquals("alice", claims.getSubject());
        assertEquals(42L, jwtService.getUserId(claims));
        assertEquals("alice", jwtService.getUsername(claims));
        assertEquals(60L, jwtService.getExpirationSeconds());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void parseTokenShouldReturnNullForInvalidToken() {
        JwtService jwtService = new JwtService(SECRET, 60_000L);

        assertNull(jwtService.parseToken("not-a-valid-token"));
    }
}
