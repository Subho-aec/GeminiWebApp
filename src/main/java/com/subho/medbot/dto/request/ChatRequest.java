package com.subho.medbot.dto.request;                                // DTOs (Data Transfer Objects) in the "request" sub-package hold data coming FROM the client.
                                                                       // Separating request and response DTOs is an enterprise pattern that prevents
                                                                       // accidentally exposing internal fields to the client.

import jakarta.validation.constraints.NotBlank;                        // Jakarta Validation annotation. @NotBlank ensures a String field is not null,
                                                                       // not empty (""), and not just whitespace ("   "). If violated, Spring automatically
                                                                       // returns a 400 Bad Request BEFORE the controller code even runs.
                                                                       // "jakarta" is the new namespace — it was "javax" before Java EE moved to Jakarta EE.

/**
 * Request body for the main /api/chat endpoint.
 *
 * When the Vue.js frontend sends a POST request to /api/chat, Spring's Jackson library
 * deserializes the incoming JSON into this object. If validation fails (@NotBlank),
 * Spring throws MethodArgumentNotValidException which our GlobalExceptionHandler catches.
 *
 * Example incoming JSON:
 *   {
 *     "prompt": "What are the symptoms of diabetes?",
 *     "sessionId": "abc-123",
 *     "language": "hi",
 *     "outputMode": "text"
 *   }
 */
public class ChatRequest {

    @NotBlank(message = "Prompt cannot be empty")                      // Validation runs BEFORE the controller method executes.
                                                                       // If user sends { "prompt": "" }, Spring returns 400 automatically.
    private String prompt;                                             // The user's medical question. Examples:
                                                                       //   "What causes headaches?"
                                                                       //   "Is it safe to take paracetamol with ibuprofen?"
                                                                       //   "मुझे बुखार है, क्या करूं?" (Hindi: I have fever, what should I do?)

    private String sessionId;                                          // UUID identifying the conversation session. If null, the controller generates one.
                                                                       // This enables conversation memory — Gemini receives the last N messages as context,
                                                                       // so follow-up questions like "Tell me more about that" work correctly.

    private String language;                                           // ISO 639 code: "en", "hi", "bn", "ta", etc. Defaults to "en" if not provided.
                                                                       // If set to a non-English language, the response will be translated to that language
                                                                       // via TranslationService before being returned to the frontend.

    private String outputMode;                                         // "text" (default), "voice", or "both".
                                                                       // "text"  → JSON response with text only
                                                                       // "voice" → Response includes base64-encoded audio data URI
                                                                       // "both"  → Response includes both text and audio

    // ─── Constructors ────────────────────────────────────────────────────────────

    public ChatRequest() {}                                            // Jackson needs this to create the object during deserialization.

    public ChatRequest(String prompt, String sessionId, String language, String outputMode) {
        this.prompt = prompt;
        this.sessionId = sessionId;
        this.language = language;
        this.outputMode = outputMode;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────────

    public String getPrompt()                   { return prompt; }
    public void setPrompt(String prompt)        { this.prompt = prompt; }
    public String getSessionId()                { return sessionId; }
    public void setSessionId(String sessionId)  { this.sessionId = sessionId; }
    public String getLanguage()                 { return language; }
    public void setLanguage(String language)     { this.language = language; }
    public String getOutputMode()               { return outputMode; }
    public void setOutputMode(String outputMode){ this.outputMode = outputMode; }
}
