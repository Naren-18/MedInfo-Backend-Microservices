package com.medinfo.medical.Producer;

import com.medinfo.common.constants.KafkaTopics;
import com.medinfo.common.events.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventProducer {
    private final KafkaTemplate<String, AuditLogEvent> kafkaTemplate;
    public void publishAuditEvent(AuditLogEvent event){
        System.out.println("Publishing Event: " + event);
        kafkaTemplate.send(
                KafkaTopics.AUDIT_EVENTS,
                event
        );
    }
}
