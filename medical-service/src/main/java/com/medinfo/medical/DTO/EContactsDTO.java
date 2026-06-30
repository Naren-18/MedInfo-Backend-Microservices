package com.medinfo.medical.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EContactsDTO {
    @NotBlank
    private String name;
    @NotBlank
    private String relationship;
    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$")
    private String phoneNumber;
}
