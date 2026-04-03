package com.webintellect.analyzer.service;

public interface AiEngineService {
    /**
     * Sends a question and the website context to the LLM and returns the answer.
     * @param context The scraped website content string
     * @param question The user's question
     * @return The AI's answer
     */
    String askQuestion(String context, String question);
}
