package com.medinfo.audit.consumer;

import com.medinfo.audit.Service.AuditService;
import com.medinfo.common.constants.KafkaTopics;
import com.medinfo.common.events.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditEventConsumer {
    private final AuditService auditService;
    @KafkaListener(
            topics = KafkaTopics.AUDIT_EVENTS,
            groupId = "audit-group"
    )
    public void consume(AuditLogEvent event){
        auditService.createAuditLog(event);
        log.info("Received audit event for user {}", event.getUserId());
    }
}