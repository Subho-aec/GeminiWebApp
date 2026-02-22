package com.subho.medbot.service;                                    // Service layer — manages conversation sessions in memory.

import com.subho.medbot.model.ChatMessage;                             // Our message model: role + content + language + timestamp.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.util.*;                                                    // UUID for generating session IDs, List and ArrayList for message storage,
                                                                       // Set for returning all session IDs.
import java.util.concurrent.ConcurrentHashMap;                         // Thread-safe HashMap. Essential because multiple users may be chatting simultaneously
                                                                       // on different threads. A regular HashMap would cause data corruption under concurrent access.
                                                                       // ConcurrentHashMap uses lock striping — it divides the map into segments and only locks
                                                                       // the segment being modified, allowing high concurrency without a global lock.

/**
 * In-memory conversation memory service.
 *
 * Stores chat sessions using ConcurrentHashMap so that:
 * 1. Multiple users can chat simultaneously without data races.
 * 2. The AI can "remember" previous messages in a session.
 * 3. The frontend can display a sidebar with chat history.
 *
 * TRADE-OFF: Since this is in-memory, all conversations are lost when the server restarts.
 * For a production app, you'd persist sessions in a database (PostgreSQL + JPA).
 * However, for a portfolio project demonstrating architecture skills, in-memory storage
 * avoids the complexity of database setup while still showcasing the design pattern.
 */
@Service
public class ChatMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryService.class);

    private static final int MAX_HISTORY_SIZE = 20;                    // Maximum messages to keep per session. Beyond this, oldest messages are dropped.
                                                                       // This prevents unbounded memory growth if a user has a very long conversation.
                                                                       // 20 messages ≈ 10 back-and-forth exchanges — enough context for Gemini.

    private static final int CONTEXT_WINDOW = 10;                      // Number of recent messages to send to Gemini as context.
                                                                       // More context = Gemini understands follow-ups better, but also = higher token cost.
                                                                       // 10 is a good balance between context quality and API cost.

    // The main storage: sessionId → list of messages.
    // ConcurrentHashMap ensures thread safety for the map itself.
    // CopyOnWriteArrayList or synchronization on the list level would be needed for
    // production, but for this use-case the risk of a brief race is acceptable.
    private final Map<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    // Stores a human-readable title for each session (derived from the first message).
    private final Map<String, String> sessionTitles = new ConcurrentHashMap<>();

    // ─── Session Management ──────────────────────────────────────────────────────

    /**
     * Creates a new session with a UUID and returns the ID.
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ArrayList<>());
        sessionTitles.put(sessionId, "New Chat");
        log.debug("Created new chat session: {}", sessionId);
        return sessionId;
    }

    /**
     * Ensures a session exists. If the sessionId is null or unknown, creates a new one.
     */
    public String ensureSession(String sessionId) {
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return createSession();
        }
        return sessionId;
    }

    // ─── Message Operations ──────────────────────────────────────────────────────

    /**
     * Adds a message to a session's history.
     * If this is the first user message, it becomes the session title.
     */
    public void addMessage(String sessionId, String role, String content, String language) {
        List<ChatMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // Auto-generate session title from the first user message
        if ("user".equals(role) && history.isEmpty()) {
            String title = content.length() > 40 ? content.substring(0, 40) + "..." : content;
            sessionTitles.put(sessionId, title);
        }

        history.add(new ChatMessage(role, content, language));

        // Trim old messages if we exceed the max
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);                                         // Remove the oldest message (FIFO order)
        }
    }

    /**
     * Returns the last N messages for a session — used as context for Gemini.
     */
    public List<ChatMessage> getRecentHistory(String sessionId) {
        List<ChatMessage> history = sessions.getOrDefault(sessionId, Collections.emptyList());
        if (history.size() <= CONTEXT_WINDOW) return new ArrayList<>(history);

        // Return only the last CONTEXT_WINDOW messages
        return new ArrayList<>(history.subList(history.size() - CONTEXT_WINDOW, history.size()));
    }

    /**
     * Returns ALL messages in a session — used by the frontend to restore a past conversation.
     */
    public List<ChatMessage> getFullHistory(String sessionId) {
        return sessions.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * Returns a summary of all sessions (id + title) for the sidebar.
     */
    public List<Map<String, String>> getAllSessions() {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : sessionTitles.entrySet()) {
            Map<String, String> session = new HashMap<>();
            session.put("id", entry.getKey());
            session.put("title", entry.getValue());
            result.add(session);
        }
        return result;
    }

    /**
     * Deletes a session and all its messages.
     */
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        sessionTitles.remove(sessionId);
        log.debug("Deleted chat session: {}", sessionId);
    }

    /**
     * Clears all sessions — useful for testing.
     */
    public void clearAll() {
        sessions.clear();
        sessionTitles.clear();
    }
}
