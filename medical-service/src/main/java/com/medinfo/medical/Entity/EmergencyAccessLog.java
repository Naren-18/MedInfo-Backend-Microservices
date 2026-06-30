package com.medinfo.medical.Entity;

import com.medinfo.medical.Enum.AccessMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @CreationTimestamp
    private LocalDateTime accessTime;
    private String ipAddress;
    private String userAgent;
    @Enumerated(EnumType.STRING)
    private AccessMethod accessMethod;

    @Column(nullable = false)
    private Long userId;
}
