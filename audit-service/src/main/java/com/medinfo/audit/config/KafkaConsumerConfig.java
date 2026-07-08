package com.medinfo.audit.config;

import com.medinfo.common.constants.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
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

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {
    private final KafkaTemplate<String,AuditLogEvent> kafkaTemplate;


    @Bean
    public ConsumerFactory<String,AuditLogEvent> consumerFactory(){
        JsonDeserializer<AuditLogEvent> deserializer =
                new JsonDeserializer<>(AuditLogEvent.class);
        deserializer.addTrustedPackages("com.medinfo.common.events");
        Map<String,Object> config=new HashMap<>();
        config.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092"
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

        return new DefaultErrorHandler(
                deadLetterPublishingRecoverer(),
                fixedBackOff
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,AuditLogEvent> kafkaListenerContainerFactory(){
        ConcurrentKafkaListenerContainerFactory<String , AuditLogEvent> factory= new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }
}
