package com.subho.medbot.config;

import com.github.benmanes.caffeine.cache.Caffeine;                   // Caffeine is a high-performance Java caching library by Ben Manes (Google engineer).
                                                                       // It's the recommended cache provider for Spring Boot — faster than Guava Cache
                                                                       // and much lighter than Redis for single-instance deployments.

import org.springframework.cache.CacheManager;                         // Spring's abstraction over cache implementations. Our services use @Cacheable which
                                                                       // talks to CacheManager. By swapping the CacheManager bean, we could switch from
                                                                       // Caffeine to Redis without changing any service code.
import org.springframework.cache.caffeine.CaffeineCacheManager;        // Caffeine-backed implementation of Spring's CacheManager.
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;                                  // For expressing time durations: TimeUnit.HOURS, TimeUnit.MINUTES, etc.

/**
 * Cache configuration using Caffeine.
 *
 * Defines cache regions used throughout MedBot:
 * - "translations"  → caches translated text (TranslationService)
 * - "langDetection" → caches detected language codes (TranslationService)
 *
 * Caching is CRITICAL for MedBot because:
 * 1. Translation calls Gemini API (~2 seconds per call, rate-limited)
 * 2. Users often translate the same text to the same language
 * 3. Language detection is repetitive for similar text patterns
 * 4. Cache hits are <1ms vs 2000ms for an API call — 2000x faster
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(500)                                      // Max 500 entries — prevents unbounded memory growth.
                                                                       // When full, Caffeine evicts the least-recently-used entry (LRU variant).
                .expireAfterWrite(1, TimeUnit.HOURS)                   // Entries expire 1 hour after creation.
                                                                       // This ensures translations stay fresh if the AI's output quality improves.
                .recordStats()                                         // Enables cache hit/miss statistics accessible via CacheManager.
        );

        // Pre-register our named caches. @Cacheable("translations") references these names.
        manager.setCacheNames(Arrays.asList("translations", "langDetection"));

        return manager;
    }
}
