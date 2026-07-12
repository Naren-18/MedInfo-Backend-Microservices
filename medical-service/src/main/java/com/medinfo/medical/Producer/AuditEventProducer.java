package com.medinfo.medical.Producer;

import com.medinfo.common.constants.KafkaTopics;
import com.medinfo.common.events.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventProducer {
    private final KafkaTemplate<String, AuditLogEvent> kafkaTemplate;
    public void publishAuditEvent(AuditLogEvent event){
        log.info("Publishing Audit Event. EventId={}, UserId={}", event.getEventId(), event.getUserId());
        kafkaTemplate.send(
                KafkaTopics.AUDIT_EVENTS,
                event
        );
    }
}
