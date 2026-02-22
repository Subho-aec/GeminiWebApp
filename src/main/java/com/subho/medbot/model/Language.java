package com.subho.medbot.model;                                   // This enum lives in the "model" layer — it defines the data structures that the rest of the app relies on.

/**
 * Enum representing all 22 Scheduled Languages of India (as per the Eighth Schedule
 * of the Indian Constitution) plus English. Each entry carries metadata needed by
 * the Translation and Text-to-Speech subsystems.
 *
 * WHY AN ENUM AND NOT A DATABASE TABLE?
 * The list of scheduled languages is defined by the Constitution and changes only through
 * a Constitutional Amendment — the last change was in 2003 when Bodo, Dogri, Maithili,
 * and Santali were added. An enum is compile-time safe and self-documenting.
 */
public enum Language {

    // ─── Each constant follows this pattern: ─────────────────────────────────────
    //   ENUM_NAME(iso639Code, englishName, nativeScript, bcp47ForSpeechAPI, browserTtsSupported)
    //
    //   iso639Code       → The ISO 639-1 (or 639-3 for languages without a 2-letter code) identifier.
    //                      Used as the key in API requests and cache lookups.
    //   englishName      → Human-readable name in English. Displayed in the UI for non-native users.
    //   nativeName       → The name written in the language's own script.
    //                      Displayed in the language-selector dropdown so users can find their language.
    //   bcp47Code        → BCP-47 tag (e.g., "hi-IN") required by the Web Speech API for both
    //                      SpeechRecognition (voice input) and SpeechSynthesis (voice output).
    //   browserTtsSupported → Whether Chrome/Edge typically ship with a TTS voice for this language.
    //                         If false, the frontend falls back to transliteration or shows an info message.

    ENGLISH ("en",  "English",    "English",      "en-US",  true),   // Default language of the application
    HINDI   ("hi",  "Hindi",      "हिन्दी",        "hi-IN",  true),   // Most widely spoken; full Chrome TTS support
    BENGALI ("bn",  "Bengali",    "বাংলা",         "bn-IN",  true),   // Second-most spoken Indian language
    TELUGU  ("te",  "Telugu",     "తెలుగు",        "te-IN",  true),   // Dravidian family; Chrome has a neural voice
    MARATHI ("mr",  "Marathi",    "मराठी",         "mr-IN",  true),   // Official language of Maharashtra
    TAMIL   ("ta",  "Tamil",      "தமிழ்",         "ta-IN",  true),   // One of the oldest living classical languages
    URDU    ("ur",  "Urdu",       "اردو",          "ur-IN",  true),   // Right-to-left script; shares grammar with Hindi
    GUJARATI("gu",  "Gujarati",   "ગુજરાતી",       "gu-IN",  true),   // Spoken in Gujarat; Chrome TTS available
    KANNADA ("kn",  "Kannada",    "ಕನ್ನಡ",         "kn-IN",  true),   // Dravidian family; official in Karnataka
    ODIA    ("or",  "Odia",       "ଓଡ଼ିଆ",         "or-IN",  false),  // Limited browser TTS — fallback to server-side
    MALAYALAM("ml", "Malayalam",  "മലയാളം",        "ml-IN",  true),   // Dravidian; spoken in Kerala; Chrome voice available
    PUNJABI ("pa",  "Punjabi",    "ਪੰਜਾਬੀ",        "pa-IN",  true),   // Gurmukhi script; spoken in Punjab
    ASSAMESE("as",  "Assamese",   "অসমীয়া",       "as-IN",  false),  // Eastern Indo-Aryan; limited TTS
    MAITHILI("mai", "Maithili",   "मैथिली",        "mai-IN", false),  // Spoken in Bihar; uses Devanagari script
    SANTALI ("sat", "Santali",    "ᱥᱟᱱᱛᱟᱲᱤ",       "sat-IN", false),  // Ol Chiki script; Austroasiatic family
    KASHMIRI("ks",  "Kashmiri",   "कॉशुर",         "ks-IN",  false),  // Can be written in Devanagari, Nastaliq, or Sharada
    NEPALI  ("ne",  "Nepali",     "नेपाली",        "ne-IN",  false),  // Indo-Aryan; Devanagari script
    SINDHI  ("sd",  "Sindhi",     "سنڌي",          "sd-IN",  false),  // Perso-Arabic script in Pakistan; Devanagari in India
    DOGRI   ("doi", "Dogri",      "डोगरी",         "doi-IN", false),  // Spoken in Jammu region; Devanagari script
    KONKANI ("kok", "Konkani",    "कोंकणी",        "kok-IN", false),  // Official language of Goa; Devanagari script
    MANIPURI("mni", "Manipuri",   "মৈতৈলোন্",      "mni-IN", false),  // Also called Meitei; Meetei Mayek script
    BODO    ("brx", "Bodo",       "बड़ो",          "brx-IN", false),  // Sino-Tibetan family; Devanagari script
    SANSKRIT("sa",  "Sanskrit",   "संस्कृतम्",      "sa-IN",  false);  // Classical liturgical language; root of many Indian languages

    // ─── Fields ──────────────────────────────────────────────────────────────────

    private final String code;                                         // ISO 639 code — the "machine-readable" identifier. Example: "hi" for Hindi.
                                                                       // Used as a key in caches, API requests, and the frontend language picker's value attribute.

    private final String displayName;                                  // English name — "Hindi", "Bengali", etc.
                                                                       // Shown alongside nativeName in the UI so English-speaking users can also identify languages.

    private final String nativeName;                                   // Name in native script — "हिन्दी", "বাংলা", etc.
                                                                       // This is the primary label in the frontend language dropdown so native speakers
                                                                       // can find their language without knowing its English name.

    private final String bcp47Code;                                    // BCP-47 language tag required by the Web Speech API.
                                                                       // Format: primaryLanguage-region (e.g., "hi-IN" means Hindi as spoken in India).
                                                                       // If the browser's SpeechSynthesis receives the wrong tag it will silently fail,
                                                                       // so accuracy here is critical.

    private final boolean browserTtsSupported;                         // Whether mainstream browsers (Chrome 90+, Edge) ship with a TTS voice for this language.
                                                                       // If true → the frontend uses the browser's SpeechSynthesis API directly (zero latency, free).
                                                                       // If false → the frontend shows a note or attempts fallback transliteration.

    // ─── Constructor ─────────────────────────────────────────────────────────────

    Language(String code, String displayName, String nativeName, String bcp47Code, boolean browserTtsSupported) {
        this.code = code;
        this.displayName = displayName;
        this.nativeName = nativeName;
        this.bcp47Code = bcp47Code;
        this.browserTtsSupported = browserTtsSupported;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────────

    public String getCode()               { return code; }
    public String getDisplayName()        { return displayName; }
    public String getNativeName()         { return nativeName; }
    public String getBcp47Code()          { return bcp47Code; }
    public boolean isBrowserTtsSupported(){ return browserTtsSupported; }

    // ─── Lookup helper ───────────────────────────────────────────────────────────

    /**
     * Finds a Language by its ISO 639 code (case-insensitive).
     * Returns ENGLISH if the code is null or unrecognised — a safe default
     * that prevents NullPointerExceptions downstream.
     */
    public static Language fromCode(String code) {
        if (code == null || code.isBlank()) return ENGLISH;            // Defensive: treat missing language as English
        for (Language lang : values()) {                               // Linear scan is fine — only 23 entries, called infrequently
            if (lang.code.equalsIgnoreCase(code.trim())) {
                return lang;
            }
        }
        return ENGLISH;                                                // Unknown code → default to English rather than throwing
    }
}

/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  SECTION 1 — WHERE THIS ENUM FITS IN THE MEDBOT PROJECT                   ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  This is the SINGLE SOURCE OF TRUTH for every language MedBot supports.    ║
 * ║  Every other class that touches language — TranslationService,             ║
 * ║  ChatController, the Vue.js frontend — references this enum either         ║
 * ║  directly (backend) or via the /api/languages endpoint (frontend).         ║
 * ║                                                                            ║
 * ║  Data flow:                                                                ║
 * ║                                                                            ║
 * ║   Vue.js UI                                                                ║
 * ║      │  GET /api/languages                                                 ║
 * ║      ▼                                                                     ║
 * ║  TranslationController.getLanguages()                                      ║
 * ║      │  iterates Language.values()                                         ║
 * ║      ▼                                                                     ║
 * ║  Returns JSON array of LanguageInfo DTOs                                   ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  Vue.js builds <select> dropdown from the response                         ║
 * ║  User picks "Hindi" → value "hi" is sent in subsequent requests            ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  POST /api/chat  { "prompt": "...", "language": "hi" }                     ║
 * ║      │                                                                     ║
 * ║      ▼                                                                     ║
 * ║  ChatController → Language.fromCode("hi") → Language.HINDI                 ║
 * ║      │  Passes to TranslationService, which builds the Gemini prompt       ║
 * ║      │  using HINDI.getDisplayName() → "Hindi"                             ║
 * ║      ▼                                                                     ║
 * ║  Gemini API receives: "Translate to Hindi: ..."                            ║
 * ║                                                                            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  SECTION 2 — WHY ENUMS BEAT STRINGS FOR A FIXED SET OF VALUES             ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  If we used raw Strings ("hi", "bn", "te", …) everywhere:                 ║
 * ║    - A typo like "hii" would silently break translation                    ║
 * ║    - We'd have to duplicate display names / BCP-47 codes in every class    ║
 * ║    - Adding a new language would require hunting through the whole codebase ║
 * ║                                                                            ║
 * ║  With an enum:                                                             ║
 * ║    - The compiler catches invalid language references at COMPILE time       ║
 * ║    - All metadata lives in ONE place                                       ║
 * ║    - IDE autocompletion works: type Language. and see all options           ║
 * ║    - Adding a new language = adding one line to this file                   ║
 * ║                                                                            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  SECTION 3 — THE EIGHTH SCHEDULE AND INDIA'S LINGUISTIC LANDSCAPE          ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  India has 22 "scheduled" languages listed in the Eighth Schedule of its   ║
 * ║  Constitution. These are the languages the government is obligated to       ║
 * ║  develop and promote. MedBot supporting ALL 22 is significant because:     ║
 * ║                                                                            ║
 * ║   1. India has 1.4B+ people; many don't speak English                      ║
 * ║   2. Medical information in your own language can save lives               ║
 * ║   3. Most medical AI chatbots support only English + Hindi at best         ║
 * ║   4. Supporting all 22 languages is a genuine differentiator               ║
 * ║                                                                            ║
 * ║  The languages span 4 major families:                                      ║
 * ║   - Indo-Aryan (Hindi, Bengali, Marathi, Gujarati, Punjabi, etc.)          ║
 * ║   - Dravidian  (Telugu, Tamil, Kannada, Malayalam)                          ║
 * ║   - Sino-Tibetan (Bodo, Manipuri)                                          ║
 * ║   - Austroasiatic (Santali)                                                ║
 * ║                                                                            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  SECTION 4 — BIG PICTURE: HOW LANGUAGE CONNECTS ALL MEDBOT CLASSES         ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║   Language (this enum)                                                     ║
 * ║      ▲                                                                     ║
 * ║      │ used by                                                             ║
 * ║      ├── TranslationService  (builds Gemini translation prompts)           ║
 * ║      ├── ChatController      (reads language from request)                 ║
 * ║      ├── TranslationController (returns supported languages list)          ║
 * ║      ├── ChatResponse DTO    (includes language metadata)                  ║
 * ║      └── Vue.js frontend     (language picker, TTS voice selection)        ║
 * ║                                                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
