package com.subho.medbot.dto.response;

/**
 * DTO representing one entry in the /api/languages response.
 * The Vue.js frontend uses this to build the language selector dropdown.
 */
public class LanguageInfo {

    private String code;                                               // ISO 639 code — used as the <option value="hi"> in the dropdown.
    private String name;                                               // English name — "Hindi", shown alongside native name.
    private String nativeName;                                         // Native script — "हिन्दी", primary display text in dropdown.
    private String bcp47Code;                                          // BCP-47 tag for Web Speech API — "hi-IN".
    private boolean ttsSupported;                                      // Whether the browser can speak this language.
    private boolean sttSupported;                                      // Whether the browser can recognize speech in this language.

    public LanguageInfo() {}

    public LanguageInfo(String code, String name, String nativeName, String bcp47Code, boolean ttsSupported) {
        this.code = code;
        this.name = name;
        this.nativeName = nativeName;
        this.bcp47Code = bcp47Code;
        this.ttsSupported = ttsSupported;
        this.sttSupported = ttsSupported;                              // For simplicity, STT availability mirrors TTS.
    }

    public String getCode()            { return code; }
    public String getName()            { return name; }
    public String getNativeName()      { return nativeName; }
    public String getBcp47Code()       { return bcp47Code; }
    public boolean isTtsSupported()    { return ttsSupported; }
    public boolean isSttSupported()    { return sttSupported; }

    public void setCode(String code)                { this.code = code; }
    public void setName(String name)                { this.name = name; }
    public void setNativeName(String nativeName)    { this.nativeName = nativeName; }
    public void setBcp47Code(String bcp47Code)      { this.bcp47Code = bcp47Code; }
    public void setTtsSupported(boolean ttsSupported){ this.ttsSupported = ttsSupported; }
    public void setSttSupported(boolean sttSupported){ this.sttSupported = sttSupported; }
}
