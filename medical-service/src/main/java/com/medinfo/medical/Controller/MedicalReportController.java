package com.medinfo.medical.Controller;

import com.medinfo.medical.Entity.MedicalReport;
import com.medinfo.medical.Security.JWTService;
import com.medinfo.medical.Service.MedicalReportService;
import com.medinfo.medical.ai.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class MedicalReportController {

    private final GeminiService geminiService;
    private final MedicalReportService medicalReportService;
    private final JWTService jwtService;
    @Value("${gemini.api-key}")
    private String apiKey;

    @GetMapping("/test")

    public ResponseEntity<MedicalReport> processSamplePdf(@RequestHeader("Authorization") String authHeader) {
        String token=authHeader.substring(7);
        Long userId= jwtService.extractUserId(token);
        MedicalReport extractedText = medicalReportService.processSamplePdf(userId);

        return ResponseEntity.ok(extractedText);

    }
    @GetMapping("/api-key")
    public String testKey() {
        return apiKey.substring(0, 10);
    }
    @GetMapping("/ai-test")
    public String test() {

        return geminiService.generateSummary("""
                Krishna Institute Of Medical Sciences Limited
                1-8-31/1, Minister Road, Krishna Nagar Colony,
                Secunderabad
                Phone/Fax: 040-44885000/040-27840980
                Email: assistance@kimshospitals.com || Website:
                www.kimshospitals.com
                DEPARTMENT OF INTERNAL MEDICINE/GENERAL MEDICINE
                DISCHARGE SUMMARY
                PATIENT DETAILS
                PRIMARY CONSULTANT
                SECONDARY DOCTOR
                DIAGNOSIS
                CHIEF COMPLAINTS
                PRESENT HISTORY
                PAST HISTORY
                ON EXAMINATION
                Patient Name :
                VETAPALEM VENKATA NARENDRA KUMAR
                Age/Gender : 24Y(s) 6M(s) 23D(s)/Male
                IP No. : IPSE2526046573 Admn Date : 21-03-2026 10:17
                UMR No. : MRSE2425087325 Discharge Date : 25-03-2026
                Doctor Name : DR. SHIVA RAJU.K Specialization :
                INTERNAL MEDICINE/GENERAL MEDICINE
                Ward/Room/Bed :
                TWIN SHARING/TWIN-12D/1291-A
                Mobile No. : 9966288487
                Address : 1-11-10/1/B, OLD COSTUMS BASTI BEGUMPET
                DR. SHIVA RAJU
                MD(Internal Medicine) (GENERAL MEDICINE)
                Sr. Consultant Internal Medicine
                Hypertensive Emergency 
                Nephritic syndrome
                Dizziness since morning 1day 
                 giddiness and C/o sweating No C/o blurring of vision and shortness of breath. C/o episodic palpitation. No
                c/o epigastric pain. No C/o fever, burning m micturition, vomiting and loose stools. patient admitted for further
                evaluation and management 
                K/C/O Gout on Tab Febuget 
                , KIMS-/CS/EF/0325-03-2026 05:06: PM Patient Name:VETAPALEM VENKATA NARENDRA KUMAR, IP#:IPSE2526046573 (MRSE2425087325)
                COURSE IN THE HOSPITAL
                CONDITION AT THE TIME OF DISCHARGE
                DISCHARGE MEDICATIONS
                WHEN & HOW TO GET EMERGENCY CARE
                FOLLOW UP
                Review with Dr. Shiva Raju from the INTERNAL MEDICINE/GENERAL MEDICINE on Tuesday, 31-03-2026
                DISCHARGE INSTRUCTIONS
                PR : 136/min, 
                BP : 200/110mmHg, 
                SpO2 : 94% at RA 
                CVS : S1S2+
                RS : BAE +
                P/A : Soft
                Patient presented to the hospital with the above-mentioned complaints, for which he was initially treated with
                tablet Nicardia 10 mg stat followed by 20 mg thrice daily, and hourly blood pressure monitoring was initiated.
                Investigations revealed increased 24-hour urinary protein of 1220 mg/day and urine protein–creatinine ratio
                of 1.3, with urine routine microscopy showing protein 3+; outside investigations showed hemoglobin 17.3
                g/dL, total leukocyte count 9840 cells/mm³, platelet count 3.08 lakh/mm³, ESR 3 mm/hour, and HbA1c 5.9%,
                deranged lipid profile. 2D echocardiography showed concentric LVH, good LV systolic function with ejection
                fraction 60%, grade I LV diastolic dysfunction, trivial MR/TR, no pulmonary hypertension, no LV clot, and
                normal IVC collapsibility; ultrasound abdomen and pelvis revealed hepatomegaly with grade II fatty liver and
                bilateral grade II renal parenchymal changes. During the hospital course, the patient had no complaints
                suggestive of end-organ damage. Serum TSH (2.79 µIU/mL) and early morning cortisol (12.6 µg/dL) were
                within normal limits. Nephrology consultation (Dr. Sridhar Reddy) was obtained in view of proteinuria and
                hypertension, with impression of hypertensive urgency, young hypertension, and nephritic syndrome; the
                patient was advised tablet Nicardia, Telma, Met XL, and Cilnidipine, renal Doppler was normal, and further
                evaluation with renal biopsy was planned. Serum complement C3 was elevated at 205 mg/dL, and C4 was
                48.8 mg/dL (within normal limits). Cardiology and ophthalmology consultations were obtained, with
                fundoscopy revealing grade II hypertensive retinopathy. ENT consultation on examination revealed grade II
                tonsillar hypertrophy, gross deviated nasal septum to the left, and right inferior turbinate hypertrophy; sleep
                study was advised with further decision regarding renal biopsy on follow-up. Spot urine for VMA was
                negative. The patient had persistently elevated early morning blood pressures around 160/100 mmHg, which
                were managed with antihypertensives, and as of 25/03/2026, the patient is hemodynamically stable and is
                being discharged with advice to follow up on an OPD basis for further evaluation and management.
                Hemodynamically stable. Blood pressure controlled on medications. No acute complaints.
                S.No Drug Dose/Qty Route Frequency Food Order Duration Instructions
                If any emergency contact casualty 040- 71225100.
                Tablet Telma H 80 mg once daily at 8:00 AM
                Tablet Met XL 25 mg once daily at 2:00 PM
                Tablet Arkamin 0.1 mg thrice daily if BP >140/80 mmHg
                Tablet Cilnidipine 10 mg twice daily
                Tablet Montelukast once daily at bedtime for 3 days
                , KIMS-/CS/EF/0325-03-2026 05:06: PM Patient Name:VETAPALEM VENKATA NARENDRA KUMAR, IP#:IPSE2526046573 (MRSE2425087325)
                Dr. Shiva Raju
                Reg No:42179
                MD(Internal Medicine) (GENERAL MEDICINE)
                Sr. Consultant Internal M
                edicine
                Started By : Mamatha Started On : 2026-03-24 16:32:51
                Approved By : Dr. Shiva Raju Approved On : 2026-03-25 13:52:06
                Printed By : Neeranka Arunkumar Printed On : 2026-03-25 17:06:16
                NOTE
                Regular blood pressure monitoring at home
                Maintain BP charting
                Reduce salt intake, increase physical activity
                Plan for sleep study on OPD basis
                Plan for renal biopsy at follow up 
                Follow up after 7 days with BP chart and CUE report at Internal Medicine OPD 
                Please Collect the pending lab reports at 2nd floor Lab Reception in a week's time
                ** ICD Coding is based on International Classification of Diseases 11th Revision.
                ACKNOWLEDGEMENT:
                The Content of the Discharge Summary, Medications, Food & Drug Interactions, Care to be provided at Home,
                Nutrition & When and How to Obtain Emergency Care & etc. also have been Explained.
                Signature of Patient / Attender: _________________________________
                , KIMS-/CS/EF/0325-03-2026 05:06: PM Patient Name:VETAPALEM VENKATA NARENDRA KUMAR, IP#:IPSE2526046573 (MRSE2425087325)
                
            """);
    }

}