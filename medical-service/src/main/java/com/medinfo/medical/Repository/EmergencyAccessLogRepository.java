package com.medinfo.medical.Repository;


import com.medinfo.medical.Entity.EmergencyAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergencyAccessLogRepository extends JpaRepository<EmergencyAccessLog,Long> {

    Optional<EmergencyAccessLog> findAllByUserId(Long userId);
}
