package com.subho.medbot.controller;

import com.subho.medbot.dto.request.TranslateRequest;
import com.subho.medbot.dto.response.LanguageInfo;
import com.subho.medbot.dto.response.TranslateResponse;
import com.subho.medbot.model.Language;
import com.subho.medbot.service.TranslationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for translation and language-related endpoints.
 *
 * Endpoints:
 *   GET  /api/languages        → List all supported languages
 *   POST /api/translate        → Translate text between languages
 *   POST /api/detect-language  → Auto-detect the language of text
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Translation", description = "Language translation and detection endpoints")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    /**
     * Returns all supported languages with metadata.
     * The Vue.js frontend calls this on startup to populate the language selector dropdown.
     */
    @GetMapping("/languages")
    @Operation(summary = "List all supported Indian languages",
               description = "Returns all 22 scheduled Indian languages plus English with TTS/STT capabilities")
    public ResponseEntity<List<LanguageInfo>> getLanguages() {
        List<LanguageInfo> languages = Arrays.stream(Language.values())
            .map(lang -> new LanguageInfo(
                lang.getCode(),
                lang.getDisplayName(),
                lang.getNativeName(),
                lang.getBcp47Code(),
                lang.isBrowserTtsSupported()))
            .collect(Collectors.toList());

        return ResponseEntity.ok(languages);
    }

    /**
     * Translates text from one language to another using Gemini AI.
     */
    @PostMapping("/translate")
    @Operation(summary = "Translate text between languages",
               description = "Uses Gemini AI for context-aware medical translation. Results are cached for 1 hour.")
    public ResponseEntity<TranslateResponse> translate(@Valid @RequestBody TranslateRequest request) {
        TranslateResponse response = translationService.translate(
            request.getText(),
            request.getTargetLanguage(),
            request.getSourceLanguage());

        return ResponseEntity.ok(response);
    }

    /**
     * Auto-detects the language of the provided text.
     */
    @PostMapping("/detect-language")
    @Operation(summary = "Detect the language of text",
               description = "Uses Gemini AI to identify the language. Returns an ISO 639 code.")
    public ResponseEntity<Map<String, String>> detectLanguage(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        String detectedCode = translationService.detectLanguage(text);
        Language detected = Language.fromCode(detectedCode);

        return ResponseEntity.ok(Map.of(
            "code", detected.getCode(),
            "name", detected.getDisplayName(),
            "nativeName", detected.getNativeName()
        ));
    }
}
