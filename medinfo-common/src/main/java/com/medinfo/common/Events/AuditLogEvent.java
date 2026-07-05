package com.medinfo.common.event;

import com.medinfo.common.enums.AccessMethod;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEvent {

    private Long userId;

    private String ipAddress;

    private String userAgent;

    private AccessMethod accessMethod;
}