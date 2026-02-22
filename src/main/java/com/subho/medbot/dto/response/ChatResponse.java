package com.subho.medbot.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;                   // Jackson annotation that controls which fields appear in the JSON output.
                                                                       // NON_NULL means: "if a field is null, don't include it in the JSON at all."
                                                                       // This keeps responses clean — if there's no audio, the "audio" key simply
                                                                       // won't appear rather than showing "audio": null.

/**
 * Response body for the /api/chat endpoint.
 *
 * This DTO carries the AI's response back to the Vue.js frontend.
 * Fields that are null (like 'audio' when outputMode is "text") are excluded from
 * the JSON output thanks to @JsonInclude(NON_NULL).
 *
 * Example JSON output (text mode):
 *   {
 *     "text": "Diabetes is a chronic condition...",
 *     "sessionId": "abc-123",
 *     "language": "en",
 *     "languageDisplay": "English",
 *     "ttsAvailable": true,
 *     "transcription": null → NOT included because of @JsonInclude(NON_NULL)
 *   }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)                             // Applies to ALL fields in this class. Null fields are silently omitted
                                                                       // from the JSON response. Without this, every null field would show up as
                                                                       // "fieldName": null which clutters the response and wastes bandwidth.
public class ChatResponse {

    private String text;                                               // The AI-generated medical response text (may contain Markdown formatting).
    private String sessionId;                                          // The conversation session ID — returned so the frontend can send it back
                                                                       // in the next request to maintain conversation continuity.
    private String language;                                           // ISO 639 code of the response language (e.g., "hi" if translated to Hindi).
    private String languageDisplay;                                    // Human-readable language name (e.g., "Hindi") for display in the UI.
    private Boolean ttsAvailable;                                      // Whether browser TTS supports this language. The frontend uses this to
                                                                       // decide whether to show the "Listen to this" button.
                                                                       // Boolean (wrapper) instead of boolean (primitive) so it can be null → omitted by Jackson.
    private String transcription;                                      // Set only when the user sent a voice message. Contains the transcribed text
                                                                       // so the user can see what the speech engine heard.
    private String audio;                                              // Base64-encoded audio data URI (e.g., "data:audio/mpeg;base64,...")
                                                                       // Only set when outputMode is "voice" or "both" AND server-side TTS is available.

    public ChatResponse() {}

    // ─── Builder-style setters (return 'this' for method chaining) ───────────────
    //
    // This pattern lets you write:
    //   new ChatResponse().text("Hello").sessionId("abc").language("en")
    // instead of calling each setter on separate lines. Cleaner and more readable.

    public ChatResponse text(String text)                    { this.text = text; return this; }
    public ChatResponse sessionId(String sessionId)          { this.sessionId = sessionId; return this; }
    public ChatResponse language(String language)             { this.language = language; return this; }
    public ChatResponse languageDisplay(String languageDisplay) { this.languageDisplay = languageDisplay; return this; }
    public ChatResponse ttsAvailable(Boolean ttsAvailable)   { this.ttsAvailable = ttsAvailable; return this; }
    public ChatResponse transcription(String transcription)  { this.transcription = transcription; return this; }
    public ChatResponse audio(String audio)                  { this.audio = audio; return this; }

    // ─── Standard Getters (needed by Jackson for serialization) ──────────────────

    public String getText()            { return text; }
    public String getSessionId()       { return sessionId; }
    public String getLanguage()        { return language; }
    public String getLanguageDisplay() { return languageDisplay; }
    public Boolean getTtsAvailable()   { return ttsAvailable; }
    public String getTranscription()   { return transcription; }
    public String getAudio()           { return audio; }
}
