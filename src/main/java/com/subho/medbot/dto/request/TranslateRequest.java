package com.subho.medbot.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the /api/translate endpoint.
 *
 * Example JSON:
 *   {
 *     "text": "Diabetes is a chronic condition that affects blood sugar levels.",
 *     "targetLanguage": "hi",
 *     "sourceLanguage": "en"
 *   }
 */
public class TranslateRequest {

    @NotBlank(message = "Text to translate cannot be empty")
    private String text;                                               // The text to translate. Can be a single word, sentence, or entire paragraph
                                                                       // of medical information. Max practical limit ~4000 chars (Gemini context window).

    @NotBlank(message = "Target language is required")
    private String targetLanguage;                                     // ISO 639 code of the language to translate INTO. Example: "hi" for Hindi.

    private String sourceLanguage;                                     // ISO 639 code of the source language. If null, the system auto-detects
                                                                       // the language using Gemini. This is useful when the frontend doesn't
                                                                       // know what language the user typed in.

    public TranslateRequest() {}

    public TranslateRequest(String text, String targetLanguage, String sourceLanguage) {
        this.text = text;
        this.targetLanguage = targetLanguage;
        this.sourceLanguage = sourceLanguage;
    }

    public String getText()                             { return text; }
    public void setText(String text)                    { this.text = text; }
    public String getTargetLanguage()                   { return targetLanguage; }
    public void setTargetLanguage(String targetLanguage){ this.targetLanguage = targetLanguage; }
    public String getSourceLanguage()                   { return sourceLanguage; }
    public void setSourceLanguage(String sourceLanguage){ this.sourceLanguage = sourceLanguage; }
}
