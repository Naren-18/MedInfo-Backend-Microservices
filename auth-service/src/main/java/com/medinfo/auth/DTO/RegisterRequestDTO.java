package com.medinfo.auth.DTO;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDTO {

    @NotBlank
    private String fullName;
    @NotNull
    @Min(1)
    @Max(120)
    private Integer age;
    @NotBlank
    private String gender;
    @Email
    @NotBlank
    private String email;
    @NotBlank
    @Size(min = 8)
    private String password;
}
