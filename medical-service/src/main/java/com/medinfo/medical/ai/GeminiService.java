package com.medinfo.medical.ai;

public interface GeminiService {

    String generateSummary(String extractedText);

    String generateRawCompletion(String fullPrompt);

}