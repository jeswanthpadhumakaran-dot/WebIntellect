package com.webintellect.analyzer.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WebsiteContent {
    private String url;
    private String title;
    private String description;
    
    // Extracted content
    private String mainText;
    private List<String> links;
    private List<String> buttons;
    private List<String> formFields;
    
    // Metadata
    private String rawHtml;
    
    public WebsiteContent() {}

    public String generatePromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("Website URL: ").append(url).append("\n");
        sb.append("Title: ").append(title).append("\n");
        sb.append("Meta Description: ").append(description).append("\n");
        sb.append("---\nMain Text Content:\n").append(mainText).append("\n");
        sb.append("---\nButtons and Navigation Items:\n").append(String.join(", ", buttons)).append("\n");
        sb.append("---\nForms / Input Fields detected:\n").append(String.join(", ", formFields)).append("\n");
        
        // Don't include all links to save context window, just a summary or the first few
        int linkLimit = Math.min(20, links.size());
        sb.append("---\nSample Links extracted (first ").append(linkLimit).append("):\n");
        for (int i = 0; i < linkLimit; i++) {
            sb.append("- ").append(links.get(i)).append("\n");
        }
        
        return sb.toString();
    }
}
