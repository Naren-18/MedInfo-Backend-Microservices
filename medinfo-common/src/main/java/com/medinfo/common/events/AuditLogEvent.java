package com.medinfo.common.events;

import com.medinfo.common.enums.AccessMethod;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditLogEvent {

    private Long userId;

    private String ipAddress;

    private String userAgent;

    private AccessMethod accessMethod;
}