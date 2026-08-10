package com.medinfo.medical.Kafka;

import com.medinfo.common.constants.KafkaTopics;
import com.medinfo.medical.DTO.MedicalReportEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalReportProducerImpl
        implements MedicalReportProducer {

    private final KafkaTemplate<String,Object> kafkaTemplate;

    @Override
    public void publish(MedicalReportEvent event){

        kafkaTemplate.send(
                KafkaTopics.MEDICAL_REPORT_EVENTS,
                event
        );

        log.info(
                "Medical Report Event Published. ReportId={}",
                event.getReportId()
        );
    }
}