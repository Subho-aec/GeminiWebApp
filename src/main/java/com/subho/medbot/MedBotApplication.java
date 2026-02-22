package com.subho.medbot;                                            // Root package — Spring Boot's @SpringBootApplication scans this package and all sub-packages.
                                                                       // That's why ALL our classes are under com.subho.medbot.* — they get auto-discovered.

import org.springframework.boot.SpringApplication;                     // The class that bootstraps the entire Spring Boot application.
                                                                       // Internally it: creates the ApplicationContext, starts embedded Tomcat,
                                                                       // triggers component scanning, auto-configuration, and starts listening on the configured port.
import org.springframework.boot.autoconfigure.SpringBootApplication;   // A convenience annotation that combines three annotations:
                                                                       // @Configuration (this class can define @Bean methods)
                                                                       // @EnableAutoConfiguration (Spring auto-configures based on classpath)
                                                                       // @ComponentScan (scans this package and sub-packages for @Service, @Controller, etc.)
import org.springframework.cache.annotation.EnableCaching;             // Activates Spring's caching infrastructure. Without this, @Cacheable annotations
                                                                       // on TranslationService would be silently IGNORED. This annotation tells Spring to
                                                                       // create cache proxies around methods marked with @Cacheable, @CacheEvict, etc.

/**
 * Entry point for the MedBot application.
 *
 * This is a standalone JAR application (not a WAR) — it contains an embedded
 * Tomcat server, so it can be run with just: java -jar medbot.jar
 * This is the modern Spring Boot deployment approach, ideal for Docker/Render deployments.
 */
@SpringBootApplication                                                 // Triggers auto-configuration, component scanning, and marks this as a configuration class.
@EnableCaching                                                         // Activates Spring Cache — makes @Cacheable work in TranslationService.
public class MedBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedBotApplication.class, args);          // Boots up Spring: creates context, starts Tomcat, deploys our app.
    }
}
