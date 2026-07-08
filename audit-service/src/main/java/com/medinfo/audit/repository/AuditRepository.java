package com.medinfo.audit.repository;

import com.medinfo.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditLog,Long> {
    boolean existsByEventId(UUID eventId);
}
