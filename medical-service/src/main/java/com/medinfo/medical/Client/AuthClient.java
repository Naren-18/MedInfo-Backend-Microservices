package com.medinfo.medical.Client;

import com.medinfo.medical.Config.FeignConfig;
import com.medinfo.medical.DTO.UserPublicResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "auth-service",
        url = "http://localhost:8081",
        configuration = FeignConfig.class
)
public interface AuthClient {

    @GetMapping("/api/auth/users/public/{publicProfileId}")
    UserPublicResponseDTO getUserByPublicProfileId(@PathVariable String publicProfileId);
}
