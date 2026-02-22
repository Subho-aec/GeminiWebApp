package com.subho.medbot.service;                                    // Service layer — contains business logic. Controllers delegate to services.

import com.fasterxml.jackson.databind.JsonNode;                        // Jackson's tree-model class representing any JSON node (object, array, string, number).
                                                                       // We use it to navigate Gemini's nested JSON response without creating a full POJO
                                                                       // for every possible field Gemini returns.
import com.fasterxml.jackson.databind.ObjectMapper;                    // The main Jackson class for JSON ↔ Java conversion. Thread-safe, so we create one
                                                                       // instance and reuse it. Internally it maintains serializers/deserializers for each type.
import com.fasterxml.jackson.databind.node.ArrayNode;                  // Jackson's mutable JSON array node. Used to build the "contents" array programmatically
                                                                       // rather than concatenating raw JSON strings (which would be fragile and injection-prone).
import com.fasterxml.jackson.databind.node.ObjectNode;                 // Jackson's mutable JSON object node. Provides type-safe .put("key", "value") methods.

import com.subho.medbot.exception.ApiException;                        // Our custom exception — wraps errors with an HTTP status code.
import com.subho.medbot.exception.ServiceUnavailableException;         // Thrown when Gemini API is unreachable or returns 5xx.
import com.subho.medbot.model.ChatMessage;                             // Represents a single chat message (role + content) for conversation memory.

import org.slf4j.Logger;                                               // SLF4J Logger — the interface we log through. Never use System.out.println in production code.
import org.slf4j.LoggerFactory;                                        // Creates Logger instances. The pattern LoggerFactory.getLogger(ThisClass.class) is universal
                                                                       // in Java enterprise applications.

import org.springframework.beans.factory.annotation.Value;             // Injects values from application.properties or environment variables into fields.
                                                                       // For example, @Value("${gemini.api.key}") reads the gemini.api.key property.
import org.springframework.http.*;                                     // HttpHeaders, HttpEntity, MediaType, ResponseEntity — Spring's HTTP abstractions.
import org.springframework.http.HttpStatus;                            // Enum of HTTP status codes.
import org.springframework.stereotype.Service;                         // Marks this class as a Spring service bean. Spring auto-discovers it during component scan
                                                                       // and creates a single shared instance (singleton scope by default).
import org.springframework.web.client.HttpClientErrorException;        // Thrown by RestTemplate when the remote server returns a 4xx error (400, 401, 404, etc.).
import org.springframework.web.client.HttpServerErrorException;        // Thrown by RestTemplate when the remote server returns a 5xx error (500, 502, 503, etc.).
import org.springframework.web.client.ResourceAccessException;         // Thrown when RestTemplate cannot connect at all (DNS failure, timeout, connection refused).
import org.springframework.web.client.RestTemplate;                    // Spring's synchronous HTTP client. Sends HTTP requests and deserializes responses.
                                                                       // In a future upgrade, this could be replaced with WebClient for non-blocking I/O.

import java.util.List;                                                 // java.util.List — the ordered collection interface. We use it to pass conversation history.

/**
 * Core AI service — communicates with Google's Gemini API.
 *
 * This service is the BRAIN of MedBot. Every user question ultimately flows through
 * generateContent() to get an AI-powered response. The service also provides
 * specialized methods for translation and language detection that other services call.
 */
@Service                                                               // Spring creates ONE instance of this class and injects it wherever needed via constructor injection.
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    // ─── Medical System Prompt ──────────────────────────────────────────────────
    // This is prepended to EVERY user question to give Gemini its "personality" and safety rails.
    // Without a system prompt, Gemini would give generic answers. With it, Gemini behaves like
    // a specialized medical assistant that includes disclaimers and recommends professional help.

    private static final String MEDICAL_SYSTEM_PROMPT = """
        You are MedBot, an advanced AI-powered medical assistant. Follow these guidelines strictly:

        1. ACCURACY: Provide evidence-based medical information. Cite general medical consensus.
        2. SAFETY: Always include a disclaimer that you are an AI, not a licensed doctor.
           For serious symptoms, ALWAYS recommend consulting a healthcare professional.
        3. EMERGENCIES: If the user describes symptoms of a heart attack, stroke, severe bleeding,
           or other emergencies, immediately advise calling emergency services (112 in India, 911 in US).
        4. CLARITY: Use simple language. Explain medical terms when you use them.
        5. STRUCTURE: Use headings, bullet points, and markdown formatting for readability.
        6. EMPATHY: Be warm and reassuring. Medical anxiety is real — acknowledge it.
        7. SCOPE: You can discuss symptoms, general conditions, medication information,
           preventive care, nutrition, and mental health. Never prescribe specific medications
           or dosages — that requires a licensed physician.
        8. PRIVACY: Never ask for personal identifying information.

        Remember: Your goal is to INFORM and GUIDE, never to DIAGNOSE or PRESCRIBE.
        """;

    @Value("${gemini.api.key}")                                        // Reads from environment variable GEMINI_API_KEY (via application.properties placeholder).
    private String apiKey;                                             // The Gemini API key. NEVER log this value — it would be a security breach.

    @Value("${google.gemini.url}")                                     // The Gemini API endpoint URL. Stored in properties so we can switch models
    private String geminiUrl;                                          // (e.g., gemini-2.5-flash → gemini-2.5-pro) without changing code.

    private final RestTemplate restTemplate;                           // Injected via constructor (defined in AppConfig or auto-configured by Spring Boot).
    private final ObjectMapper objectMapper;                           // Reused Jackson mapper — thread-safe after configuration.

    // ─── Constructor Injection ──────────────────────────────────────────────────
    // Spring sees that this constructor needs RestTemplate and ObjectMapper,
    // finds beans of those types in the application context, and passes them in.
    // This is "Dependency Injection" — the core principle of Spring Framework.

    public GeminiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ─── Primary Method: Generate Medical Response ───────────────────────────────

    /**
     * Sends a prompt to Gemini with conversation history and returns the AI's text response.
     *
     * @param prompt     The user's current question
     * @param history    Previous messages in this session (for context). Can be null or empty.
     * @return The AI-generated response text (may contain Markdown)
     */
    public String generateContent(String prompt, List<ChatMessage> history) {
        log.info("Generating content for prompt: {}", truncateForLog(prompt));

        try {
            String requestJson = buildRequestJson(prompt, history);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            String fullUrl = geminiUrl + "?key=" + apiKey;

            ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, entity, String.class);
            return extractTextFromResponse(response.getBody());

        } catch (HttpClientErrorException e) {                         // 4xx errors — usually bad API key or malformed request
            log.error("Gemini API client error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException("Gemini API error: " + e.getResponseBodyAsString(), HttpStatus.BAD_GATEWAY);

        } catch (HttpServerErrorException e) {                         // 5xx errors — Gemini's servers are having issues
            log.error("Gemini API server error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServiceUnavailableException("Gemini API", e);

        } catch (ResourceAccessException e) {                          // Network errors — DNS failure, timeout, connection refused
            log.error("Cannot reach Gemini API: {}", e.getMessage());
            throw new ServiceUnavailableException("Gemini API (network error)", e);

        } catch (ApiException e) {                                     // Re-throw our own exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling Gemini: ", e);
            throw new ApiException("Failed to generate response: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Simplified overload — generates content without conversation history.
     * Used by TranslationService for translation/detection prompts that don't need context.
     */
    public String generateContent(String prompt) {
        return generateContent(prompt, null);
    }

    // ─── JSON Request Builder ────────────────────────────────────────────────────

    /**
     * Builds the JSON request body for the Gemini API.
     *
     * Gemini expects this structure:
     * {
     *   "contents": [
     *     { "role": "user", "parts": [{ "text": "system prompt + user's question" }] },
     *     { "role": "model", "parts": [{ "text": "previous AI response" }] },
     *     { "role": "user", "parts": [{ "text": "follow-up question" }] }
     *   ]
     * }
     *
     * The conversation history (if present) is prepended before the current prompt,
     * giving Gemini context about what was discussed previously.
     */
    private String buildRequestJson(String prompt, List<ChatMessage> history) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");

        // 1. Add system prompt as the first "user" message
        ObjectNode systemMsg = contents.addObject();
        systemMsg.put("role", "user");
        ArrayNode systemParts = systemMsg.putArray("parts");
        systemParts.addObject().put("text", MEDICAL_SYSTEM_PROMPT);

        // 2. Add a fake "model" acknowledgment (Gemini needs alternating user/model turns)
        ObjectNode ack = contents.addObject();
        ack.put("role", "model");
        ArrayNode ackParts = ack.putArray("parts");
        ackParts.addObject().put("text", "Understood. I am MedBot, ready to help with medical questions while always recommending professional consultation.");

        // 3. Add conversation history (last N messages for context)
        if (history != null && !history.isEmpty()) {
            for (ChatMessage msg : history) {
                ObjectNode histMsg = contents.addObject();
                String role = "user".equals(msg.getRole()) ? "user" : "model";
                histMsg.put("role", role);
                ArrayNode histParts = histMsg.putArray("parts");
                histParts.addObject().put("text", msg.getContent());
            }
        }

        // 4. Add the current user prompt
        ObjectNode currentMsg = contents.addObject();
        currentMsg.put("role", "user");
        ArrayNode currentParts = currentMsg.putArray("parts");
        currentParts.addObject().put("text", prompt);

        return objectMapper.writeValueAsString(root);
    }

    // ─── Response Parser ─────────────────────────────────────────────────────────

    /**
     * Extracts the text content from Gemini's JSON response.
     *
     * Response structure: candidates[0].content.parts[0].text
     * This is Google's standard Gemini response format.
     */
    private String extractTextFromResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String text = parts.get(0).path("text").asText(null);
                    if (text != null && !text.isEmpty()) {
                        return text;
                    }
                }
            }

            // Check if Gemini returned a safety block
            JsonNode promptFeedback = root.path("promptFeedback");
            if (promptFeedback.has("blockReason")) {
                String reason = promptFeedback.path("blockReason").asText("UNKNOWN");
                log.warn("Gemini blocked response: {}", reason);
                return "I'm sorry, I cannot provide a response to that question due to safety guidelines. " +
                       "Please rephrase your question or consult a healthcare professional directly.";
            }

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
        }

        throw new ApiException("Gemini returned an unexpected response format", HttpStatus.BAD_GATEWAY);
    }

    // ─── Utility ─────────────────────────────────────────────────────────────────

    private String truncateForLog(String text) {
        if (text == null) return "null";
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
}

/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  SECTION 1 — WHERE THIS CLASS FITS IN THE MEDBOT PROJECT                  ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  This is the MOST IMPORTANT service in the entire application.             ║
 * ║  Every AI-powered feature ultimately calls GeminiService.generateContent().║
 * ║                                                                            ║
 * ║  Data Flow:                                                                ║
 * ║                                                                            ║
 * ║  ┌─────────────┐     ┌────────────────┐     ┌──────────────┐              ║
 * ║  │  Vue.js UI  │────▶│ ChatController │────▶│ GeminiService│              ║
 * ║  └─────────────┘     └────────────────┘     └──────┬───────┘              ║
 * ║                                                     │                      ║
 * ║                                                     ▼                      ║
 * ║                                           ┌─────────────────┐              ║
 * ║                                           │  Gemini API     │              ║
 * ║                                           │  (Google Cloud)  │              ║
 * ║                                           └─────────────────┘              ║
 * ║                                                                            ║
 * ║  Other services that also call GeminiService:                              ║
 * ║  • TranslationService → for translating medical text between languages     ║
 * ║  • TranslationService → for auto-detecting the language of user input      ║
 * ║                                                                            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  SECTION 2 — CONVERSATION MEMORY: HOW MULTI-TURN CONTEXT WORKS            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  Turn 1: User asks "What causes headaches?"                                ║
 * ║    → GeminiService sends: [system_prompt, "What causes headaches?"]        ║
 * ║    → Gemini responds: "Headaches can be caused by tension, dehydration..." ║
 * ║    → ChatMemoryService stores both messages.                               ║
 * ║                                                                            ║
 * ║  Turn 2: User asks "What about the third one?"                             ║
 * ║    → GeminiService sends: [system_prompt, Turn1_Q, Turn1_A, Turn2_Q]      ║
 * ║    → Gemini now has context and can explain "the third cause" correctly.    ║
 * ║                                                                            ║
 * ║  Without conversation memory, Gemini would say "I don't know what 'third   ║
 * ║  one' you're referring to." — a terrible user experience.                  ║
 * ║                                                                            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  SECTION 3 — WHY SYSTEM PROMPTS MATTER                                    ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  Large Language Models are general-purpose by default. Without a system    ║
 * ║  prompt, Gemini might:                                                     ║
 * ║  • Give dangerously confident medical diagnoses                            ║
 * ║  • Skip disclaimers about consulting a doctor                              ║
 * ║  • Provide unstructured, hard-to-read responses                            ║
 * ║  • Prescribe specific medication dosages                                   ║
 * ║                                                                            ║
 * ║  Our system prompt constrains Gemini's behavior to be:                     ║
 * ║  • Helpful but cautious                                                    ║
 * ║  • Structured and readable                                                 ║
 * ║  • Always recommending professional consultation                           ║
 * ║  • Emergency-aware (advises calling 112 for urgent symptoms)               ║
 * ║                                                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
