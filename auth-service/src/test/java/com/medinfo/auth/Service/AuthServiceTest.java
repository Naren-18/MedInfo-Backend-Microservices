package com.medinfo.auth.Service;

import com.medinfo.auth.DTO.LoginRequestDTO;
import com.medinfo.auth.DTO.RegisterRequestDTO;
import com.medinfo.auth.DTO.UserPublicResponseDTO;
import com.medinfo.auth.Entity.User;
import com.medinfo.auth.Exception.ResourceAlreadyExistsException;
import com.medinfo.auth.Exception.ResourceNotFoundException;
import com.medinfo.auth.Exception.UnauthorizedException;
import com.medinfo.auth.Repository.UserRepository;
import com.medinfo.auth.Security.JWTService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JWTService jwtService;

    @InjectMocks
    private AuthService authService;

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    void register_ShouldRegisterUserSuccessfully() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(new User());

        String result = authService.register(request);

        assertEquals("User Registration Completed", result);
        verify(userRepository).existsByEmail("john@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_ShouldThrow_WhenEmailAlreadyExists() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class,
                () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("john@example.com");
        request.setPassword("password123");

        User user = User.builder()
                .id(1L)
                .email("john@example.com")
                .password("encoded-password")
                .fullName("John Doe")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        String token = authService.login(request);

        assertEquals("jwt-token", token);
        verify(jwtService).generateToken(user);
    }

    @Test
    void login_ShouldThrow_WhenEmailNotFound() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("unknown@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> authService.login(request));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_ShouldThrow_WhenPasswordIsInvalid() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("john@example.com");
        request.setPassword("wrong-password");

        User user = User.builder()
                .id(1L)
                .email("john@example.com")
                .password("encoded-password")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> authService.login(request));

        verify(jwtService, never()).generateToken(any());
    }

    // -------------------------------------------------------------------------
    // getUserByPublicProfileId
    // -------------------------------------------------------------------------

    @Test
    void getUserByPublicProfileId_ShouldReturnDTO_WhenUserExists() {
        String publicProfileId = "public-uuid-123";
        User user = User.builder()
                .id(1L)
                .fullName("John Doe")
                .publicProfileId(publicProfileId)
                .build();

        when(userRepository.findByPublicProfileId(publicProfileId)).thenReturn(Optional.of(user));

        UserPublicResponseDTO result = authService.getUserByPublicProfileId(publicProfileId);

        assertEquals(1L, result.getUserId());
        assertEquals("John Doe", result.getFullName());
        verify(userRepository).findByPublicProfileId(publicProfileId);
    }

    @Test
    void getUserByPublicProfileId_ShouldThrow_WhenUserNotFound() {
        String publicProfileId = "non-existent-uuid";

        when(userRepository.findByPublicProfileId(publicProfileId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> authService.getUserByPublicProfileId(publicProfileId));
    }
}
