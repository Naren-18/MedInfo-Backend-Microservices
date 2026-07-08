package com.medinfo.audit.service;

import com.medinfo.audit.entity.AuditLog;
import com.medinfo.audit.repository.AuditRepository;
import com.medinfo.common.events.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    private final AuditRepository auditRepository;

    public AuditLog createAuditLog(AuditLogEvent auditLogEvent){
        if(auditRepository.existsByEventId(auditLogEvent.getEventId())){
            log.info("Duplicate event ignored: {}", auditLogEvent.getEventId());
            return null;
        }
        AuditLog auditLog=AuditLog.builder()
                .ipAddress(auditLogEvent.getIpAddress())
                .userAgent(auditLogEvent.getUserAgent())
                .userId(auditLogEvent.getUserId())
                .accessMethod(auditLogEvent.getAccessMethod())
                .eventId(auditLogEvent.getEventId())
                .build();
//        throw new RuntimeException("Testing Kafka Retry");
        return auditRepository.save(auditLog);

    }
}
