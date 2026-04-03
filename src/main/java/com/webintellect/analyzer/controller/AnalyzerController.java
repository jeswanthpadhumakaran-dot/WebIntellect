package com.webintellect.analyzer.controller;

import com.webintellect.analyzer.dto.AnalyzeRequest;
import com.webintellect.analyzer.dto.AnalyzeResponse;
import com.webintellect.analyzer.dto.ChatRequest;
import com.webintellect.analyzer.service.AnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyzerController {

    private final AnalyzerService analyzerService;

    public AnalyzerController(AnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeWebsite(@RequestBody AnalyzeRequest request) {
        try {
            if (request.getUrl() == null || request.getUrl().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "URL must be provided."));
            }
            
            // Generate session ID by performing the scraping
            String sessionId = analyzerService.analyzeUrl(request.getUrl());
            
            AnalyzeResponse response = new AnalyzeResponse(
                    sessionId, 
                    "Website successfully analyzed.",
                    analyzerService.getSessionContext(sessionId)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        try {
            if (request.getSessionId() == null || request.getSessionId().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Session ID is missing."));
            }
            if (request.getQuestion() == null || request.getQuestion().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Question cannot be blank."));
            }

            String aiResponse = analyzerService.chatWithAi(request.getSessionId(), request.getQuestion());
            
            return ResponseEntity.ok(Map.of("answer", aiResponse));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
