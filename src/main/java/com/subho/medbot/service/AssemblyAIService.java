package com.subho.medbot.service;                                    // Service layer — business logic for speech-to-text using AssemblyAI.

import com.fasterxml.jackson.databind.JsonNode;                        // Jackson tree model for parsing AssemblyAI's JSON responses.
import com.fasterxml.jackson.databind.ObjectMapper;                    // JSON ↔ Java converter.

import com.subho.medbot.exception.ApiException;                        // Custom exception with HTTP status.
import com.subho.medbot.exception.ServiceUnavailableException;         // Thrown when AssemblyAI is unreachable.

import org.slf4j.Logger;                                               // SLF4J logging interface.
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;             // Reads properties/environment variables.
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;                // Spring's abstraction for an uploaded file in a multipart/form-data request.
                                                                       // Provides getBytes(), getOriginalFilename(), getContentType(), etc.

import java.util.HashMap;
import java.util.Map;

/**
 * AssemblyAI integration for Speech-to-Text (STT).
 *
 * AssemblyAI uses an ASYNCHRONOUS 3-step process for transcription:
 *   Step 1: Upload the audio file → get an upload_url
 *   Step 2: Submit a transcription job with the upload_url → get a transcript_id
 *   Step 3: Poll the transcript_id until status is "completed" → get the text
 *
 * This asynchronous approach is used because transcription of long audio can take
 * seconds to minutes. Unlike synchronous APIs that make you wait with an open connection,
 * AssemblyAI lets you submit and poll — more reliable for large files.
 */
@Service
public class AssemblyAIService {

    private static final Logger log = LoggerFactory.getLogger(AssemblyAIService.class);

    @Value("${assemblyai.api.key}")
    private String apiKey;                                             // AssemblyAI API key — used in the Authorization header of every request.

    @Value("${assemblyai.base.url}")
    private String baseUrl;                                            // Base URL: "https://api.assemblyai.com/v2"

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AssemblyAIService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ─── Private helpers for the 3-step process ──────────────────────────────────

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);                          // AssemblyAI uses a simple API key in the Authorization header (no "Bearer" prefix).
        return headers;
    }

    /**
     * Step 1: Upload raw audio bytes to AssemblyAI's temporary storage.
     * Returns a temporary URL that can be used in Step 2.
     */
    private String uploadFile(MultipartFile audioFile) throws Exception {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);    // Raw binary content type — we're sending the file bytes directly, not as form-data.

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(audioFile.getBytes(), headers);
        String url = baseUrl + "/upload";

        log.debug("Uploading audio file ({} bytes) to AssemblyAI", audioFile.getSize());
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("upload_url").asText();
    }

    /**
     * Step 2: Submit a transcription job. Returns a transcript ID that we'll poll in Step 3.
     */
    private String submitTranscription(String uploadUrl) throws Exception {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("audio_url", uploadUrl);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = baseUrl + "/transcript";

        log.debug("Submitting transcription job to AssemblyAI");
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("id").asText();
    }

    /**
     * Step 3: Poll for the transcription result. Checks every second for up to 60 seconds.
     */
    private String pollForTranscription(String transcriptId) throws Exception {
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        String url = baseUrl + "/transcript/" + transcriptId;

        for (int attempt = 0; attempt < 60; attempt++) {
            Thread.sleep(1000);                                        // Wait 1 second between polls. In production, you might use exponential backoff.

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();

            if ("completed".equalsIgnoreCase(status)) {
                log.info("Transcription completed after {} seconds", attempt + 1);
                return root.path("text").asText();
            } else if ("error".equalsIgnoreCase(status)) {
                String errorMsg = root.path("error").asText("Unknown transcription error");
                throw new ApiException("AssemblyAI transcription failed: " + errorMsg, HttpStatus.BAD_GATEWAY);
            }
            // Otherwise status is "queued" or "processing" — keep polling
        }
        throw new ApiException("AssemblyAI transcription timed out after 60 seconds", HttpStatus.GATEWAY_TIMEOUT);
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Converts spoken audio to text using AssemblyAI's 3-step async process.
     *
     * @param audioFile The uploaded audio file (WAV, MP3, WebM, etc.)
     * @return The transcribed text
     */
    public String speechToText(MultipartFile audioFile) throws Exception {
        try {
            String uploadUrl = uploadFile(audioFile);
            String transcriptId = submitTranscription(uploadUrl);
            return pollForTranscription(transcriptId);

        } catch (HttpClientErrorException e) {
            log.error("AssemblyAI API error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException("AssemblyAI error: " + e.getResponseBodyAsString(), HttpStatus.BAD_GATEWAY, e);

        } catch (ResourceAccessException e) {
            log.error("Cannot reach AssemblyAI: {}", e.getMessage());
            throw new ServiceUnavailableException("AssemblyAI", e);

        } catch (ApiException e) {
            throw e;                                                   // Don't double-wrap our own exceptions

        } catch (Exception e) {
            log.error("Unexpected error in speech-to-text: ", e);
            throw new ApiException("Speech-to-text processing failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
