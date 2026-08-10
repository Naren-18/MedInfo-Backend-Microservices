package com.medinfo.medical.Kafka;

import com.medinfo.common.constants.KafkaTopics;
import com.medinfo.medical.DTO.MedicalReportEvent;
import com.medinfo.medical.Entity.MedicalReport;
import com.medinfo.medical.ReportStatus;
import com.medinfo.medical.Repository.MedicalReportRepository;
import com.medinfo.medical.Service.MedicalProfileSummaryService;
import com.medinfo.medical.ai.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MedicalReportConsumer {

    private final MedicalReportRepository repository;
    private final GeminiService  geminiService;
    private final MedicalProfileSummaryService medicalProfileSummaryService;

    @KafkaListener(
        topics = KafkaTopics.MEDICAL_REPORT_EVENTS,
        groupId = "medical-summary-group",
        containerFactory = "kafkaListenerContainerFactory"
)
    public void consume(MedicalReportEvent event) {


        MedicalReport report =
                repository.findById(event.getReportId())
                        .orElseThrow();

        try {


            report.setStatus(ReportStatus.SUMMARIZING);
            repository.save(report);
            log.info(
                    "Generating AI summary for ReportId={}",
                    report.getId()
            );
            String summary =
                    geminiService.generateSummary(
                            report.getExtractedText()
                    );

            report.setSummary(summary);
            report.setStatus(ReportStatus.SUMMARIZED);
            log.info(
                    "Summary generated successfully. ReportId={}",
                    report.getId()
            );
            repository.save(report);
            medicalProfileSummaryService.updatedMedicalProfile(report.getUserId());

        }

        catch (Exception ex){

            report.setStatus(ReportStatus.FAILED);
            repository.save(report);

            log.error("AI Summary generation failed", ex);

            throw ex;
        }
        log.info("Medical Report {} summarized successfully.", report.getId());

    }

}