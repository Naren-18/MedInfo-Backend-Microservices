package com.medinfo.medical.Kafka;

import com.medinfo.medical.DTO.MedicalReportEvent;

public interface MedicalReportProducer {

    void publish(MedicalReportEvent event);

}
