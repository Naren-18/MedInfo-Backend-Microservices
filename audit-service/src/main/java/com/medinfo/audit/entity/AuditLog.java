package com.medinfo.audit.entity;

import com.medinfo.common.enums.AccessMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    private String ipAddress;
    private String userAgent;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessMethod accessMethod;
    @CreationTimestamp
    private LocalDateTime accessedAt;
    @Column(nullable = false, unique = true)
    private UUID eventId;
}
