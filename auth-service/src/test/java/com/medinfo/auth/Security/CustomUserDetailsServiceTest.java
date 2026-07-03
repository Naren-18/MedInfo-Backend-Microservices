package com.medinfo.auth.Security;

import com.medinfo.auth.Entity.User;
import com.medinfo.auth.Repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    // -------------------------------------------------------------------------
    // loadUserByUsername
    // -------------------------------------------------------------------------

    @Test
    void loadUserByUsername_ShouldReturnUserDetails_WhenUserExists() {
        User user = User.builder()
                .id(1L)
                .email("john@example.com")
                .password("encoded-password")
                .fullName("John Doe")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        UserDetails result = customUserDetailsService.loadUserByUsername("john@example.com");

        assertEquals("john@example.com", result.getUsername());
        assertEquals("encoded-password", result.getPassword());
        assertTrue(result.getAuthorities().isEmpty());
        verify(userRepository).findByEmail("john@example.com");
    }

    @Test
    void loadUserByUsername_ShouldThrow_WhenUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("unknown@example.com")
        );

        assertEquals("User Not Found", exception.getMessage());
        verify(userRepository).findByEmail("unknown@example.com");
    }
}
