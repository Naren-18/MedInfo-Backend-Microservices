package com.medinfo.audit.consumer;

import com.medinfo.audit.service.AuditService;
import com.medinfo.common.constants.KafkaTopics;
import com.medinfo.common.events.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditEventConsumer {
    private final AuditService auditService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Audit Consumer Started. Topic={}", KafkaTopics.AUDIT_EVENTS);
    }

    @KafkaListener(
            topics = KafkaTopics.AUDIT_EVENTS,
            groupId = "audit-group"
    )
    public void consume(AuditLogEvent event){
        log.info("Received Audit Event. EventId={}, UserId={}", event.getEventId(), event.getUserId());
        auditService.createAuditLog(event);
    }
}