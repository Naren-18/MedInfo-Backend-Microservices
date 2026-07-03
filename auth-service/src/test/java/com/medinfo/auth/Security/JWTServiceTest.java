package com.medinfo.auth.Security;

import com.medinfo.auth.Entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JWTServiceTest {

    @InjectMocks
    private JWTService jwtService;

    private static final String SECRET = "ThisIsMyVeryLongSecretKeyForMedInfoProject123456789";
    private static final long EXPIRATION = 900000L;

    @BeforeEach
    void setUp() {
        // @Value fields are not injected by Mockito, so we set them via reflection
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION);
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .email("john@example.com")
                .password("encoded-password")
                .fullName("John Doe")
                .build();
    }

    // -------------------------------------------------------------------------
    // generateToken
    // -------------------------------------------------------------------------

    @Test
    void generateToken_ShouldReturnNonNullToken() {
        String token = jwtService.generateToken(buildUser());

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateToken_ShouldProduceTokenWithThreeParts() {
        String token = jwtService.generateToken(buildUser());

        // JWT format: header.payload.signature
        assertEquals(3, token.split("\\.").length);
    }

    // -------------------------------------------------------------------------
    // extractUserId
    // -------------------------------------------------------------------------

    @Test
    void extractUserId_ShouldReturnCorrectUserId() {
        String token = jwtService.generateToken(buildUser());

        Long userId = jwtService.extractUserId(token);

        assertEquals(1L, userId);
    }

    // -------------------------------------------------------------------------
    // extractRole
    // -------------------------------------------------------------------------

    @Test
    void extractRole_ShouldReturnUserRole() {
        String token = jwtService.generateToken(buildUser());

        String role = jwtService.extractRole(token);

        assertEquals("USER", role);
    }

    // -------------------------------------------------------------------------
    // extractUsername
    // -------------------------------------------------------------------------

    @Test
    void extractUsername_ShouldReturnUserEmail() {
        String token = jwtService.generateToken(buildUser());

        String username = jwtService.extractUsername(token);

        assertEquals("john@example.com", username);
    }

    // -------------------------------------------------------------------------
    // isTokenValid
    // -------------------------------------------------------------------------

    @Test
    void isTokenValid_ShouldReturnTrue_WhenTokenMatchesUser() {
        String token = jwtService.generateToken(buildUser());

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("john@example.com");

        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        String token = jwtService.generateToken(buildUser());

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("other@example.com");

        assertFalse(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValid_ShouldReturnFalse_WhenTokenIsExpired() {
        // Generate a token that expired immediately
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
        String expiredToken = jwtService.generateToken(buildUser());

        UserDetails userDetails = mock(UserDetails.class);
        lenient().when(userDetails.getUsername()).thenReturn("john@example.com");

        assertThrows(
                ExpiredJwtException.class,
                () -> jwtService.isTokenValid(expiredToken, userDetails)
        );
    }
}
