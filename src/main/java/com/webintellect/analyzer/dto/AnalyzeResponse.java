package com.webintellect.analyzer.dto;

import com.webintellect.analyzer.model.WebsiteContent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnalyzeResponse {
    private String sessionId;
    private String message;
    private WebsiteContent previewData;
}
