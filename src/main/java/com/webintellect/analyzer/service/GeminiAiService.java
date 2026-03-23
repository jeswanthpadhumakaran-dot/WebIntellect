package com.webintellect.analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService implements AiEngineService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiService.class);
    
    @Value("${ai.gemini.api.key}")
    private String apiKey;

    @Value("${ai.gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public GeminiAiService() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String askQuestion(String context, String question) {
        if ("YOUR_GEMINI_API_KEY".equals(apiKey) || apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Gemini API key is not configured. Returning mock response.");
            return "Note: The Application is in mock mode because no valid API key is configured in application.properties.\n\n" +
                   "I received your question: '" + question + "'.\n" +
                   "Based on the text, the website has successfully been scraped and is ready for real analysis once an API key is provided.";
        }

        try {
            String fullPrompt = buildSystemPrompt(context, question);
            
            // Construct the payload for Gemini 1.5 Flash
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            Map<String, Object> parts = new HashMap<>();
            parts.put("text", fullPrompt);
            message.put("parts", List.of(parts));
            
            requestBody.put("contents", List.of(message));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String endpoint = apiUrl + "?key=" + apiKey;
            
            logger.info("Sending request to Gemini API...");
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, entity, Map.class);
            
            return parseGeminiResponse(response.getBody());

        } catch (Exception e) {
            logger.error("Error communicating with Gemini API", e);
            throw new RuntimeException("Failed to get response from AI: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(String context, String question) {
        return "You are an AI assistant analyzing a website. You will be provided with the extracted text, links, buttons, and metadata of a website.\n" +
               "Answer the user's question based strictly on the provided context.\n" +
               "If the answer cannot be found in the context, say 'I cannot find the answer to that in the scraped website content.'\n\n" +
               "--- START CONTEXT ---\n" +
               context + "\n" +
               "--- END CONTEXT ---\n\n" +
               "User Question: " + question;
    }

    private String parseGeminiResponse(Map<String, Object> responseBody) {
        try {
            if (responseBody == null || !responseBody.containsKey("candidates")) {
                return "Error: Invalid response format from LLM.";
            }
            
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates.isEmpty()) return "No answer generated.";
            
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            logger.error("Error parsing Gemini response", e);
            return "Error parsing the response from the AI.";
        }
    }
}
