package com.subho.medbot.model;                                     // Model layer — defines data structures used across the application.

import java.time.Instant;                                              // java.time.Instant represents a point on the UTC timeline with nanosecond precision.
                                                                       // We use it instead of java.util.Date because Instant is immutable, thread-safe,
                                                                       // and part of the modern java.time API introduced in Java 8.

/**
 * Represents a single message in a MedBot conversation.
 *
 * This is a plain Java object (POJO) — no database annotations because MedBot uses
 * in-memory storage via ConcurrentHashMap for now. If you later add JPA/Hibernate,
 * you would annotate this class with @Entity, @Id, etc.
 */
public class ChatMessage {

    private String role;                                               // "user" or "assistant" — identifies who sent this message.
                                                                       // Gemini API uses these exact role names in its conversation format.
                                                                       // We store them so that when we rebuild conversation context (the last N messages
                                                                       // to pass to Gemini), we know which messages are from the user vs the AI.

    private String content;                                            // The actual text of the message — could be a short question like "What causes fever?"
                                                                       // or a long markdown response with headers, lists, and emphasis.
                                                                       // Stored as raw text (not HTML) so it can be safely re-rendered by the frontend.

    private String language;                                           // ISO 639 code (e.g., "hi", "bn", "en") indicating what language this message is in.
                                                                       // This matters because:
                                                                       // 1. The frontend uses it to set the correct TTS voice (e.g., "hi-IN" for Hindi).
                                                                       // 2. When rebuilding conversation history, we know if translation was involved.

    private Instant timestamp;                                         // When this message was created. Using Instant (UTC) rather than LocalDateTime
                                                                       // avoids timezone confusion — the frontend converts to the user's local time for display.

    // ─── Constructors ────────────────────────────────────────────────────────────

    public ChatMessage() {}                                            // No-args constructor — needed by Jackson for JSON deserialization (it creates an
                                                                       // empty object first, then calls setters to populate fields).

    public ChatMessage(String role, String content, String language) {
        this.role = role;
        this.content = content;
        this.language = language;
        this.timestamp = Instant.now();                                // Auto-set timestamp at creation time
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────────

    public String getRole()                 { return role; }
    public void setRole(String role)        { this.role = role; }
    public String getContent()              { return content; }
    public void setContent(String content)  { this.content = content; }
    public String getLanguage()             { return language; }
    public void setLanguage(String language){ this.language = language; }
    public Instant getTimestamp()            { return timestamp; }
    public void setTimestamp(Instant ts)     { this.timestamp = ts; }
}

/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHERE THIS CLASS FITS                                                     ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  User sends message → ChatController creates ChatMessage(role="user")      ║
 * ║  → ChatMemoryService stores it in the session's message list               ║
 * ║  → GeminiService generates a response                                      ║
 * ║  → ChatController creates ChatMessage(role="assistant")                    ║
 * ║  → ChatMemoryService stores that too                                       ║
 * ║  → Next time the user asks a question, ChatMemoryService provides          ║
 * ║    the last N messages as context to Gemini so the AI "remembers"          ║
 * ║    what was discussed.                                                     ║
 * ║                                                                            ║
 * ║  Without this conversation memory, every question would be independent,    ║
 * ║  and the AI couldn't handle follow-ups like "Tell me more about that."     ║
 * ║                                                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
