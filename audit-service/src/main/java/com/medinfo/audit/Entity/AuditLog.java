package com.medinfo.audit.Entity;

import com.medinfo.common.Enum.AccessMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
