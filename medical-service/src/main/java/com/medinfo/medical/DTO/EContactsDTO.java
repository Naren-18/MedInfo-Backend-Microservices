package com.medinfo.medical.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EContactsDTO {
    @NotBlank
    private String name;
    @NotBlank
    private String relationship;
    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$")
    private String phoneNumber;
}
