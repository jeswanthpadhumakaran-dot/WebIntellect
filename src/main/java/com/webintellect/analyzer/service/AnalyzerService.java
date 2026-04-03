package com.webintellect.analyzer.service;

import com.webintellect.analyzer.model.WebsiteContent;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnalyzerService {

    private final ScraperService scraperService;
    private final AiEngineService aiEngineService;
    
    // Simple in-memory cache to hold the scraped context for chat sessions.
    // In production, use Redis or a proper cache with TTL.
    private final Map<String, WebsiteContent> sessionCache = new ConcurrentHashMap<>();

    public AnalyzerService(ScraperService scraperService, AiEngineService aiEngineService) {
        this.scraperService = scraperService;
        this.aiEngineService = aiEngineService;
    }

    public String analyzeUrl(String url) {
        WebsiteContent content = scraperService.scrapeWebsite(url);
        String sessionId = UUID.randomUUID().toString();
        sessionCache.put(sessionId, content);
        return sessionId;
    }

    public WebsiteContent getSessionContext(String sessionId) {
        return sessionCache.get(sessionId);
    }
    
    public void endSession(String sessionId) {
        sessionCache.remove(sessionId);
    }

    public String chatWithAi(String sessionId, String question) {
        WebsiteContent content = sessionCache.get(sessionId);
        if (content == null) {
            throw new IllegalArgumentException("Session not found or expired. Please analyze the URL again.");
        }
        
        String context = content.generatePromptContext();
        return aiEngineService.askQuestion(context, question);
    }
}
