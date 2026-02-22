package com.subho.medbot;

import com.subho.medbot.model.Language;
import com.subho.medbot.util.TextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MedBotApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the entire Spring application context starts without errors.
        // If any bean fails to initialize, this test fails immediately.
    }

    @Test
    void languageEnumLookup() {
        assertEquals(Language.HINDI, Language.fromCode("hi"));
        assertEquals(Language.TAMIL, Language.fromCode("ta"));
        assertEquals(Language.ENGLISH, Language.fromCode(null));       // null → default English
        assertEquals(Language.ENGLISH, Language.fromCode("xyz"));      // unknown → default English
        assertEquals(Language.BENGALI, Language.fromCode("  bn  "));   // trimmed lookup
    }

    @Test
    void languageEnumMetadata() {
        Language hindi = Language.HINDI;
        assertEquals("hi", hindi.getCode());
        assertEquals("Hindi", hindi.getDisplayName());
        assertEquals("हिन्दी", hindi.getNativeName());
        assertEquals("hi-IN", hindi.getBcp47Code());
        assertTrue(hindi.isBrowserTtsSupported());

        Language santali = Language.SANTALI;
        assertFalse(santali.isBrowserTtsSupported());                  // Santali has limited browser TTS
    }

    @Test
    void markdownStripping() {
        assertEquals("Hello World", TextUtils.stripMarkdown("**Hello** World"));
        assertEquals("Link text", TextUtils.stripMarkdown("[Link text](http://example.com)"));
        assertEquals("Title", TextUtils.stripMarkdown("### Title"));
        assertEquals("", TextUtils.stripMarkdown(null));
        assertEquals("", TextUtils.stripMarkdown(""));
    }

    @Test
    void textTruncation() {
        assertEquals("Hello", TextUtils.truncate("Hello", 10));
        assertEquals("Hel...", TextUtils.truncate("Hello World", 3));
        assertEquals("", TextUtils.truncate(null, 10));
    }

    @Test
    void allScheduledLanguagesPresent() {
        // India has 22 scheduled languages + English = 23 enum constants
        assertEquals(23, Language.values().length);
    }
}
