package com.medinfo.auth.Service;

import com.medinfo.auth.DTO.LoginRequestDTO;
import com.medinfo.auth.DTO.RegisterRequestDTO;
import com.medinfo.auth.DTO.UserBasicResponseDTO;
import com.medinfo.auth.Entity.User;
import com.medinfo.auth.Exception.ResourceAlreadyExistsException;
import com.medinfo.auth.Exception.ResourceNotFoundException;
import com.medinfo.auth.Exception.UnauthorizedException;
import com.medinfo.auth.Repository.UserRepository;
import com.medinfo.auth.Security.JWTService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;


    public String register(RegisterRequestDTO registerRequestDTO){
        log.info("Registration requested. Email={}", registerRequestDTO.getEmail());
        if(userRepository.existsByEmail(registerRequestDTO.getEmail())){
            throw new ResourceAlreadyExistsException(
                    "User",
                    "email",
                    registerRequestDTO.getEmail()
            );
        }
        User user= User.builder()
                .fullName((registerRequestDTO.getFullName()))
                .email(registerRequestDTO.getEmail())
                .password(passwordEncoder.encode(registerRequestDTO.getPassword()))
                .created_at(LocalDateTime.now())
                .build();
        userRepository.save(user);
        log.info("User registered successfully. UserId={}", user.getId());
        return "User Registration Completed";
    }
    public String login(LoginRequestDTO loginRequestDTO){
        log.info("Login requested. Email={}", loginRequestDTO.getEmail());
        User user=userRepository.findByEmail(loginRequestDTO.getEmail())
                .orElseThrow(()->new UnauthorizedException("Invalid Credentials"));
        boolean result=passwordEncoder.matches(loginRequestDTO.getPassword(),user.getPassword());
        if(!result){
            log.warn("Invalid login attempt. Email={}", loginRequestDTO.getEmail());
            throw new UnauthorizedException("Password Invalid");
        }

        String token = jwtService.generateToken(user);
        log.info("JWT generated. UserId={}", user.getId());
        return token;
    }

    public UserBasicResponseDTO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User",
                        "userId",
                        userId
                ));
        return UserBasicResponseDTO.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .build();
    }
}
