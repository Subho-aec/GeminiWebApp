package com.subho;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;

@Service
public class GeminiService {
    @Value("${gemini.api.key}")
    private String apiKey;
    @Value("${google.gemini.url}")
    private String geminiUrl;
    public String generateContent(String prompt) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestJson = String.format(
                "{\"contents\": [{\"parts\": [{\"text\": \"%s\"}]}]}", prompt
        );
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity(geminiUrl + "?key=" + apiKey, entity, String.class);
        return response.getBody();
    }
}
