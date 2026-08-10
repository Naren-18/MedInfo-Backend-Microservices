package com.medinfo.medical.Service;

import com.medinfo.medical.Entity.MedicalReport;

public interface MedicalReportService {

    MedicalReport processSamplePdf(Long  userId);

}