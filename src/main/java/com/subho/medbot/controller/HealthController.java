package com.subho.medbot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check and system info endpoint.
 *
 * Render (and other PaaS providers) use health check endpoints to know if your app is alive.
 * If /api/health stops responding, Render will restart the container automatically.
 * Spring Actuator provides a built-in /actuator/health, but this custom endpoint gives us
 * full control over what information is exposed.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "Health check and system info")
public class HealthController {

    @Value("${spring.application.name}")
    private String appName;

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns application status and version info")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();            // LinkedHashMap preserves insertion order for cleaner JSON output.
        health.put("status", "UP");
        health.put("application", appName);
        health.put("timestamp", Instant.now().toString());
        health.put("java", System.getProperty("java.version"));
        health.put("features", Map.of(
            "translation", true,
            "tts", true,
            "voiceInput", "browser-based (Web Speech API)",
            "streaming", true,
            "conversationMemory", true,
            "supportedLanguages", 23
        ));
        return ResponseEntity.ok(health);
    }
}
