package com.medinfo.auth.Controller;

import com.medinfo.auth.DTO.LoginRequestDTO;
import com.medinfo.auth.DTO.RegisterRequestDTO;
import com.medinfo.auth.DTO.UserPublicResponseDTO;
import com.medinfo.auth.Service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @GetMapping("/users/public/{publicProfileId}")
    public ResponseEntity<UserPublicResponseDTO> userPublicResponse(@PathVariable String publicProfileId){
        return ResponseEntity.ok(
                authService.getUserByPublicProfileId(publicProfileId)
        );
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequestDTO registerRequestDTO){
        return ResponseEntity.ok(
                authService.register(registerRequestDTO)
        );
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO){
        return ResponseEntity.ok(
                authService.login(loginRequestDTO)
        );
    }

}
