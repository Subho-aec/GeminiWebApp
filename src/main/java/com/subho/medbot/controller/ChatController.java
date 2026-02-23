package com.subho.medbot.controller;

import com.subho.medbot.dto.request.ChatRequest;
import com.subho.medbot.dto.response.ChatResponse;
import com.subho.medbot.model.ChatMessage;
import com.subho.medbot.model.Language;
import com.subho.medbot.service.ChatMemoryService;
import com.subho.medbot.service.GeminiService;
import com.subho.medbot.service.TranslationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main REST controller for MedBot chat functionality.
 *
 * @RestController = @Controller + @ResponseBody — every method return value is
 * automatically serialized to JSON by Jackson and sent as the HTTP response body.
 *
 * Endpoints:
 *   POST /api/chat           → Text-based chat (main endpoint)
 *   POST /api/chat/voice     → Voice-based chat (upload audio → transcribe → respond)
 *   GET  /api/chat/stream    → SSE streaming response (real-time typewriter effect)
 *   GET  /api/chat/sessions  → List all chat sessions
 *   GET  /api/chat/sessions/{id} → Get messages in a specific session
 *   DELETE /api/chat/sessions/{id} → Delete a session
 */
@RestController                                                        // Combines @Controller + @ResponseBody. All methods return JSON by default.
@RequestMapping("/api/chat")                                           // Base path — all endpoints in this controller start with /api/chat.
@Tag(name = "Chat", description = "MedBot AI chat endpoints")          // Swagger/OpenAPI grouping — appears in the Swagger UI sidebar.
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final GeminiService geminiService;
    private final TranslationService translationService;
    private final ChatMemoryService chatMemoryService;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public ChatController(GeminiService geminiService,
                          TranslationService translationService,
                          ChatMemoryService chatMemoryService) {
        this.geminiService = geminiService;
        this.translationService = translationService;
        this.chatMemoryService = chatMemoryService;
    }

    // ─── POST /api/chat — Main text chat endpoint ────────────────────────────────

    @PostMapping                                                       // Maps to POST /api/chat (inherits base path from @RequestMapping)
    @Operation(summary = "Send a text message to MedBot",              // Swagger documentation for this endpoint
               description = "Sends a prompt to the AI and returns a medical response. " +
                            "Supports conversation memory via sessionId and multilingual output via language parameter.")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
                                                                       // @Valid triggers validation on ChatRequest (e.g., @NotBlank on prompt).
                                                                       // @RequestBody tells Spring to parse the JSON body into a ChatRequest object.

        log.info("Chat request — language: {}, session: {}", request.getLanguage(), request.getSessionId());

        // 1. Ensure session exists
        String sessionId = chatMemoryService.ensureSession(request.getSessionId());

        // 2. Determine language
        String langCode = request.getLanguage() != null ? request.getLanguage() : "en";
        Language language = Language.fromCode(langCode);

        // 3. Store user's message in session history
        chatMemoryService.addMessage(sessionId, "user", request.getPrompt(), langCode);

        // 4. Build prompt — if the user typed in a non-English language, translate to English first
        //    so Gemini can understand the medical context better
        String promptForGemini = request.getPrompt();
        if (!"en".equals(langCode)) {
            try {
                String detected = translationService.detectLanguage(request.getPrompt());
                if (!"en".equals(detected)) {
                    promptForGemini = translationService.translate(
                        request.getPrompt(), "en", detected).getTranslatedText();
                }
            } catch (Exception e) {
                log.warn("Language detection/translation failed, using original prompt: {}", e.getMessage());
                // Fall through with original prompt — Gemini often understands Hindi/common languages directly
            }
        }

        // 5. Get AI response from Gemini with conversation context
        List<ChatMessage> history = chatMemoryService.getRecentHistory(sessionId);
        String aiResponse = geminiService.generateContent(promptForGemini, history);

        // 6. Translate response to target language if needed
        String responseInTargetLang = aiResponse;
        if (!"en".equals(langCode)) {
            try {
                responseInTargetLang = translationService.translate(
                    aiResponse, langCode, "en").getTranslatedText();
            } catch (Exception e) {
                log.warn("Response translation failed, returning English: {}", e.getMessage());
                responseInTargetLang = aiResponse;
            }
        }

        // 7. Store assistant's response in session history
        chatMemoryService.addMessage(sessionId, "assistant", responseInTargetLang, langCode);

        // 8. Build and return response
        ChatResponse response = new ChatResponse()
            .text(responseInTargetLang)
            .sessionId(sessionId)
            .language(language.getCode())
            .languageDisplay(language.getDisplayName())
            .ttsAvailable(language.isBrowserTtsSupported());

        return ResponseEntity.ok(response);
    }

    // ─── GET /api/chat/stream — Server-Sent Events streaming endpoint ────────────

    @GetMapping(value = "/stream", produces = "text/event-stream")     // "text/event-stream" is the MIME type for SSE.
    @Operation(summary = "Stream a chat response in real-time",
               description = "Returns an SSE stream where each event is a chunk of the AI's response. " +
                            "Creates a real-time typewriter effect in the frontend.")
    public SseEmitter streamChat(
            @RequestParam String prompt,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false, defaultValue = "en") String language) {

        SseEmitter emitter = new SseEmitter(120_000L);                 // 120-second timeout — SSE connections are long-lived.

        sseExecutor.submit(() -> {                                     // Run in a separate thread to not block the Tomcat request thread.
            try {
                // 1. Setup session
                String sid = chatMemoryService.ensureSession(sessionId);
                Language lang = Language.fromCode(language);
                chatMemoryService.addMessage(sid, "user", prompt, language);

                // 2. Send session ID as first event
                emitter.send(SseEmitter.event()
                    .name("session")
                    .data(Map.of("sessionId", sid)));

                // 3. Get full response from Gemini
                List<ChatMessage> history = chatMemoryService.getRecentHistory(sid);
                String fullResponse = geminiService.generateContent(prompt, history);

                // 4. Translate if needed
                if (!"en".equals(language)) {
                    try {
                        fullResponse = translationService.translate(fullResponse, language, "en")
                            .getTranslatedText();
                    } catch (Exception e) {
                        log.warn("Streaming translation failed: {}", e.getMessage());
                    }
                }

                // 5. Stream the response word by word (simulated streaming)
                //    Real Gemini streaming would use the streamGenerateContent endpoint.
                //    This simulation still gives a great UX typewriter effect.
                String[] words = fullResponse.split("(?<=\\s)");       // Split but keep whitespace attached to preceding word
                StringBuilder accumulated = new StringBuilder();

                for (String word : words) {
                    accumulated.append(word);
                    emitter.send(SseEmitter.event()
                        .name("chunk")
                        .data(Map.of("text", word, "accumulated", accumulated.toString())));
                    Thread.sleep(30);                                   // 30ms delay between words = natural reading speed
                }

                // 6. Send completion event with metadata
                chatMemoryService.addMessage(sid, "assistant", fullResponse, language);
                emitter.send(SseEmitter.event()
                    .name("done")
                    .data(Map.of(
                        "sessionId", sid,
                        "language", lang.getCode(),
                        "languageDisplay", lang.getDisplayName(),
                        "ttsAvailable", lang.isBrowserTtsSupported()
                    )));

                emitter.complete();

            } catch (Exception e) {
                log.error("SSE streaming error: ", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "An error occurred: " + e.getMessage())));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ─── Session Management Endpoints ────────────────────────────────────────────

    @GetMapping("/sessions")
    @Operation(summary = "List all chat sessions")
    public ResponseEntity<List<Map<String, String>>> listSessions() {
        return ResponseEntity.ok(chatMemoryService.getAllSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Get chat history for a specific session")
    public ResponseEntity<List<ChatMessage>> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatMemoryService.getFullHistory(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Delete a chat session")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        chatMemoryService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();                     // 204 No Content — standard response for successful DELETE
    }
}

/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHERE THIS CLASS FITS IN MEDBOT                                           ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  This is the FRONT DOOR of the backend — every user interaction            ║
 * ║  from the Vue.js UI enters through one of this controller's endpoints.     ║
 * ║                                                                            ║
 * ║  Vue.js Frontend                                                           ║
 * ║      │                                                                     ║
 * ║      ├─ POST /api/chat         → chat()          ─┐                        ║
 * ║      ├─ POST /api/chat/voice   → voiceChat()      │                        ║
 * ║      ├─ GET  /api/chat/stream  → streamChat()     ├──▶ GeminiService       ║
 * ║      ├─ GET  /api/chat/sessions→ listSessions()   │    TranslationService  ║
 * ║      └─ DELETE /api/chat/...   → deleteSession()  ┘    ChatMemoryService   ║
 * ║                                                                            ║
 * ║  The controller NEVER contains business logic — it only:                   ║
 * ║  1. Validates input (via @Valid)                                           ║
 * ║  2. Delegates to services                                                  ║
 * ║  3. Builds the response DTO                                               ║
 * ║  This separation makes the code testable and maintainable.                 ║
 * ║                                                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
