package com.medinfo.medical.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class EmergencyProfileResponseDTO {
    @NotBlank
    private String fullName;
    @NotNull
    @Min(1)
    @Max(120)
    private Integer age;
    @NotBlank
    private String gender;
    @NotBlank
    private String bloodGroup;
    @NotBlank
    private String allergies;
    @NotBlank
    private String medicalConditions;
    @NotBlank
    private String currentMedications;

    private boolean organDonor;

    private List<EContactsDTO> emergencyContacts;
}
