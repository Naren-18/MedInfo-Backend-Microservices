package com.medinfo.medical.DTO;

import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.web.bind.annotation.PostMapping;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalProfileResponseDTO {
    @NotNull
    @Min(1)
    @Max(120)
    private Integer age;
    @NotBlank
    private String gender;
    @NotBlank
    private String bloodGroup;
    @NotNull
    @Positive
    private Double height;
    @NotNull
    @Positive
    private Double weight;
    @NotBlank
    private String allergies;
    @NotBlank
    private String medicalConditions;
    @NotBlank
    private String currentMedications;

    private boolean organDonor;
}
