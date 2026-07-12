package com.medinfo.medical.Client;

import com.medinfo.medical.DTO.UserBasicResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "auth-service"
)
public interface AuthClient {

    @GetMapping("/api/auth/internal/users/{userId}")
    UserBasicResponseDTO getUserById(@PathVariable Long userId);
}
