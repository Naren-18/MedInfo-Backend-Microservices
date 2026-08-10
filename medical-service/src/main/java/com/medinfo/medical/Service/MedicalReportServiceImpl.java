package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.MedicalReportEvent;
import com.medinfo.medical.Entity.MedicalReport;
import com.medinfo.medical.Pdf.PdfExtractionService;
import com.medinfo.medical.Kafka.MedicalReportProducer;
import com.medinfo.medical.ReportStatus;
import com.medinfo.medical.Repository.MedicalReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class MedicalReportServiceImpl implements MedicalReportService {
    private final MedicalReportRepository medicalReportRepository;
    private final PdfExtractionService pdfExtractionService;
    private final MedicalReportProducer medicalReportProducer;

    @Override
    public MedicalReport processSamplePdf(Long userId) {

        try {

            ClassPathResource resource =
//                    new ClassPathResource("test-data/DOC-20260325-WA0012.pdf");
                    new ClassPathResource("test-data/Tests.pdf");

            InputStream inputStream = resource.getInputStream();

            String extractedText = pdfExtractionService.extractText(inputStream);

            MedicalReport report = MedicalReport.builder()
                    .userId(userId)
                    .fileName(resource.getFilename())
                    .extractedText(extractedText)
                    .summary(null)
                    .status(ReportStatus.UPLOADED)
                    .includedInProfileSummary(false)
                    .build();

            MedicalReport result= medicalReportRepository.save(report);
            medicalReportProducer.publish(
                    new MedicalReportEvent(
                            report.getId()
                    )
            );
            return result;
        } catch (Exception e) {

            throw new RuntimeException("Unable to process sample PDF", e);

        }

    }
}
//
//This 24-year-old male with a history of chronic gout was admitted for a hypertensive emergency
//with an initial blood pressure of 200/110 mmHg and nephritic syndrome. Significant diagnostic
//investigations revealed proteinuria with a 24-hour urine protein of 1220 mg/day,
//a urine protein-creatinine ratio of 1.3, elevated serum C3 at 205 mg/dL, and
//a negative spot urine VMA. Abdominal ultrasound showed hepatomegaly, grade II fatty liver, and
//bilateral grade II renal parenchymal changes, whereas renal Doppler ultrasound was normal.
//Echocardiography demonstrated concentric left ventricular hypertrophy with a preserved ejection
//fraction of 60%. Fundoscopy confirmed grade II hypertensive retinopathy, and ENT evaluation noted
//grade II tonsillar hypertrophy with a deviated nasal septum. Current discharge medications include
//Telma H 80 mg once daily, Met XL 25 mg once daily, Cilnidipine 10 mg twice daily, Montelukast once
//daily for three days, and Arkamin 0.1 mg thrice daily as needed for blood pressureexceeding 140/80
//mmHg. No drug allergies are documented. The patient was discharged in hemodynamically stable
//condition. Follow-up recommendations include maintaining a low-salt diet, performing
//regular blood pressure charting, attending an outpatient appointment scheduled for March 31, 2026,
//and completing pending evaluations for a sleep study and a renal biopsy.
//
//
//This 24-year-old male with a history of chronic gout and nephritic syndrome was previously admitted
//for a hypertensive emergency with an initial blood pressure of 200/110 mmHg.Past diagnostic
//evaluation revealed proteinuria with a 24-hour urine protein of 1220 mg/day and
//UPCR of 1.3, elevated serum C3 at 205 mg/dL, negative spot urine VMA, concentric left ventricular
//hypertrophy with preserved ejection fraction of 60%, grade II hypertensive retinopathy, grade II
//tonsillar hypertrophy, a deviated nasal septum, and bilateral grade II renal parenchymal changes
//with hepatomegaly and grade II fatty liver. Recent follow-up laboratory testing demonstrated mild
//renal insufficiency and hyperuricemia with an elevated serum creatinine of 1.22 mg/dL, serum uric
//acid of 8.08 mg/dL, and 1+ proteinuria on urinalysis. Active discharge medications include
//Telma H 80 mg once daily, Met XL 25 mg once daily, Cilnidipine 10 mg twice daily, Montelukast
//once daily for three days, and Arkamin 0.1 mg thrice daily as needed for blood pressure exceeding
//140/80 mmHg. No drug allergies are documented. Recommended follow-up includes maintaining a
//low-salt diet, regular blood pressure charting, attending an outpatient visit scheduled for
//March 31, 2026, completing pending sleep study and renal biopsy evaluations, rechecking renal
//function panels, and updating preventative vaccinations including hepatitis B, influenza, Tdap,
//and HPV.