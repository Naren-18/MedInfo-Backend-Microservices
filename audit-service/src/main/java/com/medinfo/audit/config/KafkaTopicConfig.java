package com.medinfo.audit.config;

import com.medinfo.common.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic auditEventsTopic(){
        return TopicBuilder
                .name(KafkaTopics.AUDIT_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic auditLogDLT() {
        return TopicBuilder
                .name(KafkaTopics.AUDIT_EVENTS_DLT)
                .partitions(1)
                .replicas(1)
                .build();

    }

}
