package com.webintellect.analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
        if (apiKey == null || apiKey.trim().isEmpty() || "YOUR_GEMINI_API_KEY".equals(apiKey) ||
            "AIzaSyCEtE2sC2zzzjK2x5m34Z2GAa8uqHNWb_o".equals(apiKey)) {
            logger.warn("Gemini API key is not configured. Returning mock response.");
            return "Note: The Application is in mock mode because no valid API key is configured in application.properties.\n\n" +
                   "I received your question: '" + question + "'.\n" +
                   "Based on the text, the website has successfully been scraped and is ready for real analysis once an API key is provided.";
        }

        try {
            String fullPrompt = buildSystemPrompt(context, question);

            Map<String, Object> requestBody = new HashMap<>();
            boolean useGenerateContent = apiUrl.contains(":generateContent");
            boolean useGenerateMessage = apiUrl.contains(":generateMessage");

            if (useGenerateContent) {
                Map<String, Object> message = new HashMap<>();
                message.put("role", "user");
                Map<String, Object> parts = new HashMap<>();
                parts.put("text", fullPrompt);
                message.put("parts", List.of(parts));
                requestBody.put("contents", List.of(message));
            } else if (useGenerateMessage) {
                Map<String, Object> message = new HashMap<>();
                message.put("author", "user");
                Map<String, Object> content = new HashMap<>();
                content.put("type", "text");
                content.put("text", fullPrompt);
                message.put("content", List.of(content));
                requestBody.put("messages", List.of(message));
            } else {
                // default to v1 generateText
                requestBody.put("prompt", Map.of("text", fullPrompt));
                requestBody.put("temperature", 0.2);
                requestBody.put("maxOutputTokens", 1024);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String endpoint = apiUrl + "?key=" + apiKey;

            logger.info("Sending request to Gemini API: {}", endpoint);
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, entity, Map.class);
                return parseGeminiResponse(response.getBody());
            } catch (HttpClientErrorException hce) {
                String body = hce.getResponseBodyAsString();
                int status = hce.getRawStatusCode();

                logger.warn("Gemini API returned {} with body {}", status, body);

                // Auto-fallback for endpoint mismatch between v1beta and v1
                if (status == 404 && apiUrl.contains("/v1beta/")) {
                    String altUrl = apiUrl.replace("/v1beta/", "/v1/").replace(":generateContent", ":generateText");
                    logger.info("Retrying Gemini request with v1 path: {}", altUrl);
                    String retryEndpoint = altUrl + "?key=" + apiKey;
                    ResponseEntity<Map> retryResponse = restTemplate.postForEntity(retryEndpoint, entity, Map.class);
                    return parseGeminiResponse(retryResponse.getBody());
                }

                // Model name fallback on 404 for common Gemini model variants
                if (status == 404) {
                    String altResult = tryFallbackModelVariants(entity, body);
                    if (altResult != null) {
                        return altResult;
                    }
                    return "Error: Model not found (404). Please verify your model path and API version. Raw body: " + body;
                }

                if (status == 403) {
                    return "Error: Invalid API key or permission issue (403). Check your key and IAM/AGI access.";
                } else if (status == 429) {
                    return "Error: API rate limit exceeded (429).";
                }

                return "Error: Failed to communicate with Gemini AI service. HTTP " + status + ". Body: " + body;
            }

        } catch (Exception e) {
            logger.error("Error communicating with Gemini API", e);
            String errorMessage = e.getMessage();

            // Provide more helpful error messages for common issues
            if (errorMessage != null && errorMessage.contains("403")) {
                return "Error: Invalid API key or insufficient permissions. Please check your Gemini API key in application.properties.";
            } else if (errorMessage != null && errorMessage.contains("429")) {
                return "Error: API rate limit exceeded. Please try again later.";
            } else if (errorMessage != null && errorMessage.contains("400")) {
                return "Error: Bad request to Gemini API. Please check your API key and try again.";
            } else {
                return "Error: Failed to communicate with Gemini AI service. Please check your API key and internet connection. Details: " + errorMessage;
            }
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

            Map<String, Object> first = candidates.get(0);

            // v1 generateText (or similar) returns "output"
            if (first.containsKey("output")) {
                Object output = first.get("output");
                if (output instanceof String) {
                    return (String) output;
                }
            }

            // Older v1beta generateContent style
            if (first.containsKey("content")) {
                Object contentValue = first.get("content");
                if (contentValue instanceof Map) {
                    Map<String, Object> contentMap = (Map<String, Object>) contentValue;
                    Object partsObj = contentMap.get("parts");
                    if (partsObj instanceof List && !((List<?>) partsObj).isEmpty()) {
                        Object firstPart = ((List<?>) partsObj).get(0);
                        if (firstPart instanceof Map && ((Map<?, ?>) firstPart).get("text") != null) {
                            return (String) ((Map<?, ?>) firstPart).get("text");
                        }
                    }
                } else if (contentValue instanceof List) {
                    List<?> contentList = (List<?>) contentValue;
                    if (!contentList.isEmpty() && contentList.get(0) instanceof Map) {
                        Map<?, ?> contentItem = (Map<?, ?>) contentList.get(0);
                        if (contentItem.get("type") != null && contentItem.get("text") != null) {
                            return contentItem.get("text").toString();
                        }
                        if (contentItem.get("output_text") != null) {
                            return contentItem.get("output_text").toString();
                        }
                    }
                }
            }

            // fallback for content as string
            if (first.get("content") instanceof String) {
                return (String) first.get("content");
            }

            return "No answer generated from Gemini response.";
        } catch (Exception e) {
            logger.error("Error parsing Gemini response", e);
            return "Error parsing the response from the AI.";
        }
    }

    private String tryFallbackModelVariants(HttpEntity<Map<String, Object>> entity, String originalBody) {
        if (!apiUrl.contains("/v1/models/")) {
            return null;
        }

        List<String> variants = List.of(
            "gemini-2.5-flash:generateContent",
            "gemini-2.5-pro:generateContent",
            "gemini-2.0-flash:generateContent",
            "gemini-2.0-flash-001:generateContent",
            "gemini-1.5:generateContent",
            "gemini-1.5-rev1:generateContent",
            "gemini-1.5-mini:generateContent",
            "gemini-1.5-flash:generateContent"
        );

        for (String variant : variants) {
            String alternativeUrl = apiUrl.replaceAll("models/[^:]+:[^?]+", "models/" + variant);
            if (alternativeUrl.equals(apiUrl)) {
                continue;
            }

            String tryEndpoint = alternativeUrl + "?key=" + apiKey;
            logger.info("Retrying Gemini request using fallback model variant: {}", tryEndpoint);
            try {
                ResponseEntity<Map> retryResponse = restTemplate.postForEntity(tryEndpoint, entity, Map.class);
                return parseGeminiResponse(retryResponse.getBody());
            } catch (HttpClientErrorException ignored) {
                // try next variant
            } catch (Exception ignored) {
                // if this fails, continue fallback loop
            }
        }

        logger.warn("Fallback model variant attempts failed. Original body: {}", originalBody);
        return null;
    }
}
