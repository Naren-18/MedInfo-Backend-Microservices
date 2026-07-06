package com.medinfo.medical.Config;

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


}
