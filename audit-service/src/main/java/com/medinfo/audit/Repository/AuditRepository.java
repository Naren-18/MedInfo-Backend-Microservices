package com.medinfo.audit.Repository;

import com.medinfo.audit.Entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog,Long> {
}
