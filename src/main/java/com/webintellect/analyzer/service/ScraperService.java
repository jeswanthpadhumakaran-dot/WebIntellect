package com.webintellect.analyzer.service;

import com.webintellect.analyzer.model.WebsiteContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(ScraperService.class);

    public WebsiteContent scrapeWebsite(String url) {
        logger.info("Starting scrape for URL: {}", url);
        WebsiteContent content = new WebsiteContent();
        content.setUrl(url);

        try {
            // Fetch and parse HTML from the URL
            Document doc = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .referrer("https://www.google.com")
        .timeout(15000)
        .get();

            content.setTitle(doc.title());
            
            Element metaDesc = doc.selectFirst("meta[name=description]");
            if (metaDesc != null) {
                content.setDescription(metaDesc.attr("content"));
            } else {
                content.setDescription("No description available.");
            }

            // Extract main text securely without scripts and styles
            // Clean out headers/footers where possible, or just take body text
            Element body = doc.body();
            if (body != null) {
                content.setMainText(body.text());
                
                // Extract Buttons
                content.setButtons(extractButtons(doc));
                
                // Extract Links
                content.setLinks(extractLinks(doc));
                
                // Extract Forms
                content.setFormFields(extractForms(doc));
            } else {
                content.setMainText("Could not find body tag.");
                content.setButtons(new ArrayList<>());
                content.setLinks(new ArrayList<>());
                content.setFormFields(new ArrayList<>());
            }
            
            logger.info("Successfully scraped '{}' with title: '{}'", url, content.getTitle());

        } catch (IllegalArgumentException e) {
            logger.error("Invalid URL format: {}", url, e);
            throw new RuntimeException("Invalid URL provided. Please include http:// or https://", e);
        } catch (IOException e) {
            logger.error("Failed to fetch the URL: {}", url, e);
            throw new RuntimeException("Error fetching the website: " + e.getMessage(), e);
        }

        return content;
    }

    private List<String> extractButtons(Document doc) {
        List<String> buttonsList = new ArrayList<>();
        // Find standard buttons
        Elements buttons = doc.select("button, input[type=submit], input[type=button], .btn, .button");
        for (Element btn : buttons) {
            String text = btn.ownText().trim();
            if (text.isEmpty()) text = btn.val(); // For inputs
            if (text.isEmpty()) text = btn.attr("aria-label"); // Accessibility label
            if (text.isEmpty()) text = btn.attr("title");
            
            if (!text.isEmpty() && !buttonsList.contains(text)) {
                buttonsList.add(text);
            }
        }
        return buttonsList;
    }

    private List<String> extractLinks(Document doc) {
        List<String> linksList = new ArrayList<>();
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("abs:href");
            String text = link.text().trim();
            if(!text.isEmpty()){
                 linksList.add(text + " (" + href + ")");
            } else {
                 linksList.add(href);
            }
        }
        return linksList;
    }

    private List<String> extractForms(Document doc) {
        List<String> formElements = new ArrayList<>();
        Elements inputs = doc.select("input:not([type=hidden]), textarea, select");
        for (Element input : inputs) {
            String type = input.tagName().equals("input") ? input.attr("type") : input.tagName();
            String name = input.attr("name");
            String placeholder = input.attr("placeholder");
            String id = input.attr("id");
            
            String desc = String.format("Type: %s, Name/ID: %s, Placeholder: %s", 
                    type, (!name.isEmpty() ? name : id), placeholder);
            formElements.add(desc);
        }
        return formElements;
    }
}
