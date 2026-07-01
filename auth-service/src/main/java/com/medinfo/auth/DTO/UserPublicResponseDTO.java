package com.medinfo.auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserPublicResponseDTO {

    @NotNull
    private Long userId;
    @NotBlank
    private String fullName;

}
