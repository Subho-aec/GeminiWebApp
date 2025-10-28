package com.subho;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;
    // Changed dependency from WhisperTTSService to AssemblyAIService
    private final AssemblyAIService assemblyAIService; 

    public GeminiController(GeminiService geminiService, AssemblyAIService assemblyAIService) {
        this.geminiService = geminiService;
        this.assemblyAIService = assemblyAIService; // Updated
    }

    /**
     * Text-based generation.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, String> body) {
        try {
            String prompt = body.getOrDefault("prompt", "");
            String outputMode = body.getOrDefault("outputMode", "text");

            // Get text response from Gemini
            String geminiResponse = geminiService.generateContent(prompt);

            Map<String, Object> resp = new HashMap<>();
            resp.put("text", geminiResponse);

            // If voice is requested, convert response text to speech and return as base64 data URI
            if ("voice".equalsIgnoreCase(outputMode) || "both".equalsIgnoreCase(outputMode)) {
                // Using new AssemblyAIService for TTS (which is currently a placeholder/disabled)
                byte[] audio = assemblyAIService.textToSpeech(geminiResponse); 
                if (audio != null && audio.length > 0) {
                    String audioDataUri = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(audio);
                    resp.put("audio", audioDataUri);
                }
            }

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Voice upload endpoint.
     */
    @PostMapping(value = "/voice", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> handleVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "action", required = false, defaultValue = "generate") String action,
            @RequestParam(value = "outputMode", required = false, defaultValue = "both") String outputMode) {

        try {
            // 1) Transcribe using AssemblyAI
            String transcription = assemblyAIService.speechToText(file); // Updated service call

            Map<String, Object> resp = new HashMap<>();
            resp.put("transcription", transcription);

            // If user only wanted transcription, return here
            if ("transcribe".equalsIgnoreCase(action)) {
                return ResponseEntity.ok(resp);
            }

            // 2) Generate Gemini response from transcription
            String geminiResponse = geminiService.generateContent(transcription);
            resp.put("text", geminiResponse);

            // 3) If voice requested, TTS the response
            if ("voice".equalsIgnoreCase(outputMode) || "both".equalsIgnoreCase(outputMode)) {
                // Using new AssemblyAIService for TTS (which is currently a placeholder/disabled)
                byte[] audio = assemblyAIService.textToSpeech(geminiResponse); 
                if (audio != null && audio.length > 0) {
                    String audioDataUri = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(audio);
                    resp.put("audio", audioDataUri);
                }
            }

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
}