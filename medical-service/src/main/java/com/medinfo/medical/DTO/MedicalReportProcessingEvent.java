package com.medinfo.medical.DTO;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalReportProcessingEvent {

    private Long reportId;

    private Long userId;

    private String extractedText;
}