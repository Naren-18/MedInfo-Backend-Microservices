package com.medinfo.medical.Repository;

import com.medinfo.medical.Entity.MedicalReport;
import com.medinfo.medical.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalReportRepository extends JpaRepository<MedicalReport,Long> {
    List<MedicalReport> findByUserIdAndIncludedInProfileSummaryFalseAndStatus(Long userId, ReportStatus status);
}
