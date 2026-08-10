package com.medinfo.medical.Service;

import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Entity.MedicalReport;
import com.medinfo.medical.Exception.MedicalProfileSummaryException;
import com.medinfo.medical.ReportStatus;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import com.medinfo.medical.Repository.MedicalReportRepository;
import com.medinfo.medical.ai.GeminiService;
import com.medinfo.medical.ai.PromptBuilder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalProfileSummaryServiceImpl implements MedicalProfileSummaryService {
    private final MedicalReportRepository medicalReportRepository;
    private final MedicalProfileRepository medicalProfileRepository;
    private final GeminiService geminiService;

    @Override
    @Transactional
    public void updatedMedicalProfile(Long userId) {

        try {

            List<MedicalReport> newReportSummary =
                    medicalReportRepository
                            .findByUserIdAndIncludedInProfileSummaryFalseAndStatus(
                                    userId,
                                    ReportStatus.SUMMARIZED
                            );

            if (newReportSummary.isEmpty()) {
                log.info("No new report summaries found for User {}", userId);
                return;
            }

            MedicalProfile profile =
                    medicalProfileRepository
                            .findByUserId(userId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Medical Profile not found for User " + userId
                                    )
                            );

            String existingSummary =
                    profile.getAiSummary() == null
                            ? ""
                            : profile.getAiSummary();

            String combinedSummary =
                    newReportSummary.stream()
                            .map(MedicalReport::getSummary)
                            .collect(Collectors.joining("\n\n"));

            String prompt =
                    PromptBuilder.buildMedicalProfileSummaryPrompt(
                            existingSummary,
                            combinedSummary
                    );

            String profileSummary = geminiService.generateRawCompletion(prompt);

            profile.setAiSummary(profileSummary);

            medicalProfileRepository.save(profile);

            newReportSummary.forEach(report ->
                    report.setIncludedInProfileSummary(true)
            );

            medicalReportRepository.saveAll(newReportSummary);

            log.info(
                    "Medical Profile Summary updated successfully for User {}",
                    userId
            );

        }
        catch (Exception ex) {

            log.error(
                    "Failed to update Medical Profile Summary for User {}",
                    userId,
                    ex
            );


            throw new MedicalProfileSummaryException(
                    "Failed to update Medical Profile Summary",
                    ex
            );
        }
    }
}
