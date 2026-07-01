package com.medinfo.medical.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPublicResponseDTO {

    private Long userId;
    private String fullName;

}
