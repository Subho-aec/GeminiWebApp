package com.subho.medbot.dto.response;

/**
 * Response body for the /api/translate endpoint.
 */
public class TranslateResponse {

    private String translatedText;                                     // The translated text in the target language.
    private String sourceLanguage;                                     // Detected or provided source language code (e.g., "en").
    private String targetLanguage;                                     // The language it was translated into (e.g., "hi").
    private boolean cached;                                            // Whether this translation was served from cache.
                                                                       // Useful for debugging and showing users that repeat translations are instant.

    public TranslateResponse() {}

    public TranslateResponse(String translatedText, String sourceLanguage, String targetLanguage, boolean cached) {
        this.translatedText = translatedText;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.cached = cached;
    }

    public String getTranslatedText()                            { return translatedText; }
    public void setTranslatedText(String translatedText)         { this.translatedText = translatedText; }
    public String getSourceLanguage()                             { return sourceLanguage; }
    public void setSourceLanguage(String sourceLanguage)          { this.sourceLanguage = sourceLanguage; }
    public String getTargetLanguage()                             { return targetLanguage; }
    public void setTargetLanguage(String targetLanguage)          { this.targetLanguage = targetLanguage; }
    public boolean isCached()                                     { return cached; }
    public void setCached(boolean cached)                         { this.cached = cached; }
}
