package com.medinfo.audit.config;

import com.medinfo.common.constants.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import com.medinfo.common.events.AuditLogEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerConfig {
    private final KafkaTemplate<String,AuditLogEvent> kafkaTemplate;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String,AuditLogEvent> consumerFactory(){
        JsonDeserializer<AuditLogEvent> deserializer =
                new JsonDeserializer<>(AuditLogEvent.class);
        deserializer.addTrustedPackages("com.medinfo.common.events");
        Map<String,Object> config=new HashMap<>();
        config.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        config.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "audit-group"
        );
        config.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        config.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class
        );
        config.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(){
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record,ex)->new TopicPartition(
                        KafkaTopics.AUDIT_EVENTS_DLT,
                        record.partition()
                )
        );
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff fixedBackOff = new FixedBackOff(
                1000L,   // Wait 1 second
                3L        // Retry 3 times
        );

        DeadLetterPublishingRecoverer recoverer = deadLetterPublishingRecoverer();
        ConsumerRecordRecoverer loggingRecoverer = (record, ex) -> {
            Object value = record.value();
            String eventId = (value instanceof AuditLogEvent auditLogEvent)
                    ? String.valueOf(auditLogEvent.getEventId())
                    : "unknown";
            log.error("Audit Event moved to DLT. EventId={}, Reason={}", eventId, ex.getMessage());
            recoverer.accept(record, ex);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                loggingRecoverer,
                fixedBackOff
        );
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry Attempt {}. Partition={}, Offset={}, Reason={}",
                        deliveryAttempt, record.partition(), record.offset(), ex.getMessage())
        );
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,AuditLogEvent> kafkaListenerContainerFactory(){
        ConcurrentKafkaListenerContainerFactory<String , AuditLogEvent> factory= new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }
}
