package com.subho.medbot.service;                                    // Service layer — translation logic powered by Gemini AI.

import com.subho.medbot.dto.response.TranslateResponse;               // The structured response we return for translation requests.
import com.subho.medbot.model.Language;                                // Enum of all 22 Indian languages + English.
import com.subho.medbot.util.TextUtils;                                // Utility for text manipulation.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cache.annotation.Cacheable;                 // Spring's cache annotation. Methods marked with @Cacheable have their return values
                                                                       // stored in a cache. On subsequent calls with the same arguments, the cached result
                                                                       // is returned WITHOUT executing the method body. This avoids redundant Gemini API calls
                                                                       // for translations we've already done.

import org.springframework.stereotype.Service;

/**
 * Translation service using Gemini AI for all 22 Indian languages.
 *
 * WHY GEMINI INSTEAD OF GOOGLE TRANSLATE API?
 * 1. We already have a Gemini API key — no extra cost or configuration.
 * 2. Gemini understands medical context — "BP" means "blood pressure" not "British Petroleum".
 * 3. Gemini handles all 22 scheduled Indian languages including less-common ones like Bodo and Santali.
 * 4. We can give Gemini specific instructions about preserving medical terminology accuracy.
 *
 * The trade-off: Gemini translation is slower than dedicated translation APIs (~1-3 seconds vs ~200ms).
 * We mitigate this with Caffeine caching — repeat translations are instant.
 */
@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private final GeminiService geminiService;                         // Injected — we use Gemini as our translation engine.

    public TranslationService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // ─── Translation ─────────────────────────────────────────────────────────────

    /**
     * Translates text from one language to another using Gemini AI.
     *
     * @Cacheable stores the result in the "translations" cache defined in CacheConfig.
     * The cache key is auto-generated from the method parameters: text + targetLangCode + sourceLangCode.
     * So translate("Hello", "hi", "en") → cached. Next call with same args → instant return.
     *
     * @param text           The text to translate
     * @param targetLangCode ISO 639 code of the target language (e.g., "hi" for Hindi)
     * @param sourceLangCode ISO 639 code of the source language (e.g., "en"). Can be null for auto-detect.
     * @return TranslateResponse with the translated text
     */
    @Cacheable(value = "translations", key = "#text.hashCode() + '-' + #targetLangCode + '-' + #sourceLangCode")
    public TranslateResponse translate(String text, String targetLangCode, String sourceLangCode) {
        Language targetLang = Language.fromCode(targetLangCode);
        Language sourceLang = sourceLangCode != null ? Language.fromCode(sourceLangCode) : Language.ENGLISH;

        log.info("Translating from {} to {}: {}", sourceLang.getDisplayName(), targetLang.getDisplayName(),
                 TextUtils.truncate(text, 50));

        // If source and target are the same, return as-is (no need to call Gemini)
        if (sourceLang == targetLang) {
            return new TranslateResponse(text, sourceLangCode, targetLangCode, false);
        }

        String prompt = buildTranslationPrompt(text, sourceLang, targetLang);
        String translated = geminiService.generateContent(prompt);

        // Gemini sometimes wraps the translation in quotes or adds explanations — clean it up
        translated = cleanTranslationOutput(translated);

        return new TranslateResponse(translated, sourceLang.getCode(), targetLang.getCode(), false);
    }

    // ─── Language Detection ──────────────────────────────────────────────────────

    /**
     * Auto-detects the language of the given text using Gemini.
     * Returns the ISO 639 code (e.g., "hi" for Hindi, "en" for English).
     */
    @Cacheable(value = "langDetection", key = "#text.hashCode()")
    public String detectLanguage(String text) {
        if (TextUtils.isBlank(text)) return "en";

        String prompt = "Detect the language of the following text. " +
                "Return ONLY the ISO 639-1 or ISO 639-3 language code " +
                "(examples: \"en\" for English, \"hi\" for Hindi, \"bn\" for Bengali, \"ta\" for Tamil). " +
                "Do not return anything else — just the code.\n\nText: " + text;

        try {
            String code = geminiService.generateContent(prompt).trim().toLowerCase()
                    .replaceAll("[^a-z]", "");                         // Strip any non-letter chars (quotes, periods) that Gemini might add
            log.debug("Detected language: {} for text: {}", code, TextUtils.truncate(text, 30));

            // Validate that we actually support this language
            Language detected = Language.fromCode(code);
            return detected.getCode();
        } catch (Exception e) {
            log.warn("Language detection failed, defaulting to English: {}", e.getMessage());
            return "en";                                               // Safe default — most medical content is in English
        }
    }

    // ─── Prompt Builders ─────────────────────────────────────────────────────────

    private String buildTranslationPrompt(String text, Language source, Language target) {
        return String.format("""
            You are an expert medical translator specializing in Indian languages.
            Translate the following text from %s to %s.

            CRITICAL RULES:
            1. Preserve ALL medical terminology accurately. Do not simplify medical terms.
            2. Keep the same structure, formatting, and markdown.
            3. Translate naturally — do not transliterate unless the target language commonly uses the English term
               (e.g., "X-ray", "CT scan", "MRI" are often kept in English even in Hindi text).
            4. Return ONLY the translated text. No explanations, no notes, no "Here is the translation:".
            5. If the text contains emergency instructions, maintain their urgency in the translation.

            Text to translate:
            %s
            """, source.getDisplayName(), target.getDisplayName(), text);
    }

    /**
     * Cleans Gemini's translation output by removing common artifacts:
     * - Surrounding quotes
     * - "Here is the translation:" preamble
     * - Trailing explanations
     */
    private String cleanTranslationOutput(String text) {
        if (text == null) return "";
        String cleaned = text.trim();

        // Remove surrounding quotes that Gemini sometimes adds
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        // Remove common preambles
        String[] preambles = {
            "Here is the translation:", "Translation:", "Translated text:",
            "Here's the translation:", "The translation is:"
        };
        for (String preamble : preambles) {
            if (cleaned.toLowerCase().startsWith(preamble.toLowerCase())) {
                cleaned = cleaned.substring(preamble.length()).trim();
            }
        }

        return cleaned;
    }
}

/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  SECTION 1 — WHERE THIS CLASS FITS IN MEDBOT                              ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  User clicks "Translate to Hindi" on a bot message                         ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  Vue.js sends POST /api/translate { text, targetLanguage: "hi" }           ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  TranslationController.translate()                                         ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  TranslationService.translate(text, "hi", "en")  ← THIS CLASS             ║
 * ║      │                                                                     ║
 * ║      ├─▶ Check Caffeine cache — if hit, return instantly                   ║
 * ║      │                                                                     ║
 * ║      ├─▶ Cache miss: build translation prompt                              ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  GeminiService.generateContent(translationPrompt)                          ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  Gemini API returns Hindi translation                                      ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  Clean output → store in cache → return TranslateResponse                  ║
 * ║                                                                            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  SECTION 2 — HOW SPRING'S @CACHEABLE WORKS INTERNALLY                     ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  Step 1: Spring creates a PROXY around TranslationService at startup.      ║
 * ║  Step 2: When translate() is called, the proxy INTERCEPTS the call.        ║
 * ║  Step 3: The proxy computes the cache key from the method arguments.       ║
 * ║  Step 4: The proxy checks if the key exists in the "translations" cache.   ║
 * ║  Step 5: If YES (cache hit) → return the cached TranslateResponse          ║
 * ║          immediately. The actual translate() method body NEVER RUNS.       ║
 * ║  Step 6: If NO (cache miss) → execute the real translate() method,         ║
 * ║          store the result in the cache, then return it.                    ║
 * ║                                                                            ║
 * ║  This means the second time someone translates "headache" to Hindi,        ║
 * ║  the response is instant (from cache) instead of waiting 2 seconds for     ║
 * ║  Gemini. The cache lives in memory (Caffeine) and expires after 1 hour.    ║
 * ║                                                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
