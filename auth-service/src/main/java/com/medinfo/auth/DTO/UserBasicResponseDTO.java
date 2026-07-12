package com.medinfo.auth.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class UserBasicResponseDTO {
    private Long userId;
    private String fullName;
}
