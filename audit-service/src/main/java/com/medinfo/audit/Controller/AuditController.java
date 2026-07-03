package com.medinfo.audit.Controller;

import com.medinfo.audit.DTO.CreateAuditLogRequestDTO;
import com.medinfo.audit.Service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @PostMapping("/log")
    public ResponseEntity<String> createAuditLog(@RequestBody CreateAuditLogRequestDTO createAuditLogRequestDTO){
        return ResponseEntity.ok(
                auditService.createAuditLog(createAuditLogRequestDTO)
        );
    }
}
