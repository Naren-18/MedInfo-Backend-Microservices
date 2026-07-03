package com.medinfo.audit.Service;

import com.medinfo.audit.DTO.CreateAuditLogRequestDTO;
import com.medinfo.audit.Entity.AuditLog;
import com.medinfo.audit.Repository.AuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditRepository auditRepository;

    public AuditLog createAuditLog(CreateAuditLogRequestDTO createAuditLogRequestDTO){

        AuditLog auditLog=AuditLog.builder()
                .ipAddress(createAuditLogRequestDTO.getIpAddress())
                .userAgent(createAuditLogRequestDTO.getUserAgent())
                .userId(createAuditLogRequestDTO.getUserId())
                .accessMethod(createAuditLogRequestDTO.getAccessMethod())
                .build();
        return auditRepository.save(auditLog);

    }
}
