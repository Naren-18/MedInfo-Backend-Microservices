package com.medinfo.medical.Client;

import com.medinfo.medical.DTO.CreateAuditLogRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "audit-service")
public interface AuditClient {

    @PostMapping("/api/audit/log")
    String createAuditLog(@RequestBody CreateAuditLogRequestDTO request);


}
