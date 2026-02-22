package com.subho.medbot.controller;                                 // Controller layer — handles HTTP requests and delegates to services.

import com.subho.medbot.dto.request.ChatRequest;                       // The request DTO — carries prompt, sessionId, language, outputMode from the frontend.
import com.subho.medbot.dto.response.ChatResponse;                     // The response DTO — carries AI text, session info, language metadata back to the frontend.
import com.subho.medbot.model.ChatMessage;                             // Message model for conversation history.
import com.subho.medbot.model.Language;                                // Language enum — used to get display names and TTS availability.
import com.subho.medbot.service.AssemblyAIService;                     // Speech-to-text service for voice input.
import com.subho.medbot.service.ChatMemoryService;                     // In-memory conversation storage.
import com.subho.medbot.service.GeminiService;                         // Core AI service — generates responses using Gemini.
import com.subho.medbot.service.TranslationService;                    // Translation service — translates responses to Indian languages.

import io.swagger.v3.oas.annotations.Operation;                       // OpenAPI/Swagger annotation for documenting endpoint purpose.
import io.swagger.v3.oas.annotations.tags.Tag;                        // Groups related endpoints in Swagger UI.

import jakarta.validation.Valid;                                       // Triggers Jakarta Bean Validation on the request body. If validation fails,
                                                                       // Spring throws MethodArgumentNotValidException → caught by GlobalExceptionHandler.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;                        // Wraps response body + status code + headers. Using ResponseEntity<ChatResponse>
                                                                       // instead of returning ChatResponse directly gives us control over the HTTP status code.
import org.springframework.web.bind.annotation.*;                      // @RestController, @RequestMapping, @PostMapping, @GetMapping, @RequestParam, etc.
import org.springframework.web.multipart.MultipartFile;                // Represents an uploaded file in multipart/form-data requests.
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter; // Spring's Server-Sent Events emitter — enables streaming responses to the frontend.

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;                           // Thread pool for running SSE streaming asynchronously.
import java.util.concurrent.Executors;                                 // Factory for creating thread pools.

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
    private final AssemblyAIService assemblyAIService;
    private final TranslationService translationService;
    private final ChatMemoryService chatMemoryService;

    // Thread pool for SSE streaming — keeps the main request threads free.
    // A virtual thread executor (Java 21+) would be even better, but Java 17 uses platform threads.
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    // ─── Constructor Injection ──────────────────────────────────────────────────
    // Spring sees these 4 parameters, finds the corresponding @Service beans, and injects them.
    // This is better than @Autowired on fields because:
    // 1. Makes dependencies explicit and visible
    // 2. Fields can be final (immutable)
    // 3. Easier to test (pass mocks via constructor)

    public ChatController(GeminiService geminiService,
                          AssemblyAIService assemblyAIService,
                          TranslationService translationService,
                          ChatMemoryService chatMemoryService) {
        this.geminiService = geminiService;
        this.assemblyAIService = assemblyAIService;
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

    // ─── POST /api/chat/voice — Voice input endpoint ─────────────────────────────

    @PostMapping(value = "/voice", consumes = {"multipart/form-data"})
    @Operation(summary = "Send a voice message to MedBot",
               description = "Upload audio → transcribe with AssemblyAI → generate AI response")
    public ResponseEntity<ChatResponse> voiceChat(
            @RequestParam("file") MultipartFile file,                  // The audio file uploaded by the browser's MediaRecorder
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "language", required = false, defaultValue = "en") String langCode,
            @RequestParam(value = "outputMode", required = false, defaultValue = "both") String outputMode) {

        log.info("Voice chat request — language: {}, session: {}", langCode, sessionId);

        // 1. Transcribe audio to text
        String transcription;
        try {
            transcription = assemblyAIService.speechToText(file);
        } catch (Exception e) {
            log.error("Speech-to-text failed: ", e);
            return ResponseEntity.ok(new ChatResponse()
                .text("Sorry, I couldn't understand the audio. Please try again or type your question.")
                .sessionId(chatMemoryService.ensureSession(sessionId)));
        }

        // 2. Process as a regular chat request (reuse the main chat logic)
        ChatRequest chatRequest = new ChatRequest(transcription, sessionId, langCode, outputMode);
        ResponseEntity<ChatResponse> response = chat(chatRequest);

        // 3. Add transcription to the response so the user can see what was heard
        ChatResponse body = response.getBody();
        if (body != null) {
            body.transcription(transcription);
        }

        return response;
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
