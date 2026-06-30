package com.medinfo.auth.Service;

import com.medinfo.auth.DTO.LoginRequestDTO;
import com.medinfo.auth.DTO.RegisterRequestDTO;
import com.medinfo.auth.Entity.User;
import com.medinfo.auth.Repository.UserRepository;
import com.medinfo.auth.Security.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;


    public String register(RegisterRequestDTO registerRequestDTO){
        if(userRepository.existsByEmail(registerRequestDTO.getEmail())){
            throw new RuntimeException("Email Alredy Exists");
        }
        User user= User.builder()
                .fullName((registerRequestDTO.getFullName()))
                .email(registerRequestDTO.getEmail())
                .password(passwordEncoder.encode(registerRequestDTO.getPassword()))
                .created_at(LocalDateTime.now())
                .publicProfileId(UUID.randomUUID().toString())
                .build();
        userRepository.save(user);
        return "User Registration Completed";
    }
    public String login(LoginRequestDTO loginRequestDTO){
        User user=userRepository.findByEmail(loginRequestDTO.getEmail())
                .orElseThrow(()->new RuntimeException("Invalid Credentials"));
        boolean result=passwordEncoder.matches(loginRequestDTO.getPassword(),user.getPassword());
        if(!result){
            throw new RuntimeException("Password Invalid");
        }

        return jwtService.generateToken(user.getEmail());
    }
}
