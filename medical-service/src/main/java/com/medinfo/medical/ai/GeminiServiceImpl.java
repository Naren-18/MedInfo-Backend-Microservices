package com.medinfo.medical.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiServiceImpl implements GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public String generateSummary(String extractedText) {

        String prompt =
                PromptBuilder.buildMedicalSummaryPrompt(extractedText);

        Map<String, Object> request = Map.of(
                "model", "gemini-3.6-flash",
                "input", prompt
        );

        String response =
                restClient.post()
                        .uri("https://generativelanguage.googleapis.com/v1beta/interactions")
                        .header("x-goog-api-key", apiKey)
                        .body(request)
                        .retrieve()
                        .body(String.class);

        try {

            JsonNode root = objectMapper.readTree(response);

            JsonNode steps = root.path("steps");

            for (JsonNode step : steps) {

                if ("model_output".equals(step.path("type").asText())) {

                    JsonNode content = step.path("content");

                    for (JsonNode contentItem : content) {

                        if ("text".equals(contentItem.path("type").asText())) {

                            return contentItem.path("text").asText();
                        }
                    }
                }
            }

            throw new RuntimeException(
                    "Gemini response did not contain model output"
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse Gemini response",
                    e
            );
        }
    }

    @Override
public String generateRawCompletion(String fullPrompt) {

    Map<String, Object> request = Map.of(
            "model", "gemini-3.6-flash",
            "input", fullPrompt
    );

    String response =
            restClient.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/interactions")
                    .header("x-goog-api-key", apiKey)
                    .body(request)
                    .retrieve()
                    .body(String.class);

    try {
        JsonNode root = objectMapper.readTree(response);
        JsonNode steps = root.path("steps");

        for (JsonNode step : steps) {
            if ("model_output".equals(step.path("type").asText())) {
                JsonNode content = step.path("content");
                for (JsonNode contentItem : content) {
                    if ("text".equals(contentItem.path("type").asText())) {
                        return contentItem.path("text").asText();
                    }
                }
            }
        }

        throw new RuntimeException("Gemini response did not contain model output");

    } catch (Exception e) {
        throw new RuntimeException("Failed to parse Gemini response", e);
    }
}
}