package com.subho;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class AssemblyAIService {

    @Value("${assemblyai.api.key}")
    private String apiKey;

    @Value("${assemblyai.base.url}")
    private String baseUrl;

    @Value("${tts.enabled}")
    private boolean ttsEnabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- Helper Methods ---

    // Ensure headers are clean and correctly structured for authorization
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // The Authorization header must contain ONLY the API key string
        headers.set("Authorization", apiKey); 
        return headers;
    }

    private String uploadFile(MultipartFile audioFile) throws IOException, HttpClientErrorException {
        HttpHeaders headers = createAuthHeaders();
        // Set Content-Type specifically for the raw file upload
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(audioFile.getBytes(), headers);
        String url = baseUrl + "/upload";

        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("upload_url").asText();
    }

    private String submitTranscription(String uploadUrl) throws IOException, HttpClientErrorException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("audio_url", uploadUrl);
        requestBody.put("auto_format", "true"); 
        
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = baseUrl + "/transcript";

        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("id").asText();
    }

    private String pollForTranscription(String transcriptId) throws Exception {
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        String url = baseUrl + "/transcript/" + transcriptId;

        // Poll every 1 second until complete (Max 60 seconds for simplicity)
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000); 
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, requestEntity, String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();

            if ("completed".equalsIgnoreCase(status)) {
                return root.path("text").asText();
            } else if ("error".equalsIgnoreCase(status)) {
                throw new Exception("AssemblyAI Transcription Failed: " + root.path("error").asText());
            }
        }
        throw new Exception("AssemblyAI Transcription timed out (over 60 seconds).");
    }

    // --- Public STT Method ---

    /**
     * Replaces the former speechToText method using AssemblyAI's asynchronous 3-step process.
     */
    public String speechToText(MultipartFile audioFile) throws Exception {
        try {
            // 1. Upload
            String uploadUrl = uploadFile(audioFile);
            
            // 2. Submit
            String transcriptId = submitTranscription(uploadUrl);
            
            // 3. Poll
            return pollForTranscription(transcriptId);
            
        } catch (HttpClientErrorException e) {
            String errorDetails = e.getResponseBodyAsString();
            // Re-throw the error with clear details
            throw new Exception("AssemblyAI API Error (" + e.getStatusCode() + "): " + errorDetails);
        } catch (Exception e) {
            throw new Exception("AssemblyAI processing error: " + e.getMessage());
        }
    }

    // --- Placeholder TTS Methods ---
    
    public byte[] textToSpeech(String text) {
        if (ttsEnabled) {
             return new byte[0]; 
        }
        return null;
    }
}