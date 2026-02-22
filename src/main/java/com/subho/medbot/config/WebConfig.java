package com.subho.medbot.config;                                     // Config classes define @Bean methods and configure framework behaviour.

import com.fasterxml.jackson.databind.ObjectMapper;                    // Jackson JSON mapper — we expose it as a Spring bean so it's shared across all services.

import org.springframework.context.annotation.Bean;                    // Marks a method whose return value becomes a Spring-managed bean.
                                                                       // Other classes can then request this bean via constructor injection.
import org.springframework.context.annotation.Configuration;           // Tells Spring this class contains bean definitions. Spring processes it at startup.
import org.springframework.web.client.RestTemplate;                    // Spring's synchronous HTTP client used for calling external APIs.
import org.springframework.web.servlet.config.annotation.CorsRegistry; // Allows configuring CORS (Cross-Origin Resource Sharing) rules.
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // Interface for customizing Spring MVC configuration.

/**
 * Central configuration for MedBot.
 *
 * Defines shared infrastructure beans (RestTemplate, ObjectMapper) and CORS settings.
 * Having these in a dedicated @Configuration class rather than scattered across services
 * follows the Single Responsibility Principle and makes them easy to find and modify.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {                    // Implementing WebMvcConfigurer lets us override Spring MVC defaults
                                                                       // (like CORS) without replacing the entire MVC configuration.

    /**
     * RestTemplate bean — shared across GeminiService and AssemblyAIService.
     *
     * By making it a @Bean, we ensure there's ONE instance with consistent configuration.
     * If we later need to add request/response interceptors (for logging, retry, etc.),
     * we only need to change this one place.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Jackson ObjectMapper — shared JSON serializer/deserializer.
     * Spring Boot auto-configures one, but defining it explicitly makes it clear
     * that we intentionally share it and could customize it (e.g., add modules,
     * change date format, configure null handling).
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * CORS configuration — controls which origins can call our API.
     *
     * CORS (Cross-Origin Resource Sharing) is a browser security mechanism.
     * Without these headers, a frontend hosted on a different domain/port
     * (e.g., localhost:3000) would be BLOCKED from calling our API (localhost:8084).
     *
     * In production, replace "/**" with your actual frontend domain.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")                                 // Apply CORS to all /api/** endpoints
                .allowedOrigins("*")                                   // Allow any origin (restrict in production!)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);                                         // Cache preflight responses for 1 hour
    }
}
