package com.subho;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException; // Added import
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${google.gemini.url}")
    private String geminiUrl;

    // Use a custom RestTemplate or add an error handler to capture 4xx/5xx responses better
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends prompt to Google Gemini generateContent and returns the textual answer.
     */
    public String generateContent(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build JSON request safely using Jackson
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode contentObj = contents.addObject();
        ArrayNode parts = contentObj.putArray("parts");
        ObjectNode part = parts.addObject();
        part.put("text", prompt);

        String requestJson = objectMapper.writeValueAsString(root);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
        String fullUrl = geminiUrl + "?key=" + apiKey;

        ResponseEntity<String> response;
        String body;

        try {
            // Attempt to call the API
            response = restTemplate.postForEntity(fullUrl, entity, String.class);
            body = response.getBody();
        } catch (HttpClientErrorException e) {
            // Catches 4xx errors (like 400 Bad Request, 404 Not Found, 401 Unauthorized)
            // This is crucial for catching the API key/URL errors properly
            String errorBody = e.getResponseBodyAsString();
            throw new RuntimeException("Gemini API Error (" + e.getStatusCode() + "): " + errorBody);
        }

        // Try to extract the text from typical Gemini response structure:
        // candidates[0].content.parts[0].text
        try {
            JsonNode rootResp = objectMapper.readTree(body);
            JsonNode candidates = rootResp.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode partsNode = content.path("parts");
                if (partsNode.isArray() && partsNode.size() > 0) {
                    String text = partsNode.get(0).path("text").asText(null);
                    if (text != null && !text.isEmpty()) {
                        return text;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors and fall back to returning raw body as error
            System.err.println("Warning: Failed to parse expected Gemini response. Falling back to raw body.");
        }

        // fallback: return raw response if structured extraction fails (or if the response was a strange error)
        throw new RuntimeException("Gemini returned unexpected format: " + (body != null ? body : "Empty Body"));
    }
}