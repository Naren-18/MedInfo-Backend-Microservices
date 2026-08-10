package com.medinfo.medical.ai;

public class PromptBuilder {

    public static String buildMedicalSummaryPrompt(String report){

        return """
                                You are preparing an emergency medical profile.

                Write a concise medical summary between 150 and 250 words.

                The summary should read like a physician's clinical note.

                Include:

                • Major diagnoses
                • Chronic illnesses
                • Current medications
                • Significant investigation findings
                • Important procedures
                • Follow-up recommendations
                • Allergies if documented
                • Any dates mentioned in the report (admission date, discharge date, test/investigation dates, follow-up date)

                If a date is mentioned, explicitly state which event it belongs to (e.g. "admitted on 21 March 2026").
                Do not omit dates even if approximate (e.g. "March 2026" is acceptable if the exact day is not stated).

                Write in p
                Do not use bullet points.
                Do not use markdown.
                Do not repeat information.
                Do not invent information.
                Do not invent a date if none is present in the report.

                The summary should be understandable by any physician within 30 seconds.
                %s
                """.formatted(report);

    }

    public static String buildMedicalProfileSummaryPrompt(String existingSummary,String newReportSummaries){
        return """
                Existing Medical Profile Summary:

                %s

                ---------------------------------------


                Requirements:
                - Preserve previous medical history.
                - Preserve all dates mentioned in either the existing summary or the new report summaries.
                - Present the medical history in chronological order wherever dates are available; place undated information at the end.
                - Add newly discovered diagnoses.
                - Add new medications.
                - Add new investigations.
                - Add new allergies if present.
                - Remove duplicate information.
                - Do not invent dates that are not present in the source summaries.
                - Keep the final summary under 300 words.
                """
                .formatted(existingSummary, newReportSummaries);
    }
}
