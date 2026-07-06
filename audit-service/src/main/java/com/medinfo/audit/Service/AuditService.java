package com.medinfo.audit.Service;

import com.medinfo.audit.Entity.AuditLog;
import com.medinfo.audit.Repository.AuditRepository;
import com.medinfo.common.events.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditRepository auditRepository;

    public AuditLog createAuditLog(AuditLogEvent auditLogEvent){

        AuditLog auditLog=AuditLog.builder()
                .ipAddress(auditLogEvent.getIpAddress())
                .userAgent(auditLogEvent.getUserAgent())
                .userId(auditLogEvent.getUserId())
                .accessMethod(auditLogEvent.getAccessMethod())
                .build();
        return auditRepository.save(auditLog);

    }
}
