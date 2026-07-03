package com.medinfo.medical.DTO;

import com.medinfo.medical.Enum.AccessMethod;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAuditLogRequestDTO {
    private long userId;
    private String ipAddress;
    private String userAgent;
    private AccessMethod accessMethod;
}
