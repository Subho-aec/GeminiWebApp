package com.subho.medbot.exception;                                  // All exception-related classes live here — single responsibility for error handling.

import com.subho.medbot.dto.response.ErrorResponse;                    // Our structured error DTO — ensures every error from MedBot is a clean JSON object,
                                                                       // not a random string or Spring's default Whitelabel Error Page.

import org.slf4j.Logger;                                               // SLF4J (Simple Logging Facade for Java) — the industry-standard logging API.
import org.slf4j.LoggerFactory;                                        // Factory that creates Logger instances. By convention, one Logger per class.

import org.springframework.http.HttpStatus;                            // Enum for HTTP status codes.
import org.springframework.http.ResponseEntity;                        // Wraps a response body + HTTP status + headers into one object.
import org.springframework.web.bind.MethodArgumentNotValidException;   // Thrown automatically by Spring when a @Valid DTO fails validation
                                                                       // (e.g., @NotBlank field is blank). We catch it here to return a friendly message.
import org.springframework.web.bind.annotation.ExceptionHandler;       // Marks a method as the handler for a specific exception type.
import org.springframework.web.bind.annotation.RestControllerAdvice;   // Combines @ControllerAdvice + @ResponseBody — makes this class a global error handler
                                                                       // that intercepts exceptions from ALL controllers and returns JSON responses.

import java.time.Instant;                                              // For timestamping error responses.

/**
 * GLOBAL EXCEPTION HANDLER — the safety net for the entire MedBot backend.
 *
 * @RestControllerAdvice tells Spring: "If ANY controller throws an exception,
 * check this class first before generating a default error page."
 *
 * Without this class, Spring would return its ugly default Whitelabel Error Page (HTML)
 * or a raw 500 error with a stack trace. With this class, every error becomes a clean,
 * structured JSON response that the Vue.js frontend can parse and display gracefully.
 *
 * Execution flow:
 *   Controller throws exception → Spring intercepts it → Scans @ExceptionHandler methods
 *   in this class → Finds the most specific match → Calls that method → Returns its response.
 */
@RestControllerAdvice                                                  // This one annotation turns this class into a global error interceptor.
                                                                       // "RestController" means responses are automatically serialized to JSON.
                                                                       // "Advice" means it provides cross-cutting advice to all controllers.
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
                                                                       // Logger scoped to this class. Log messages will show up as:
                                                                       //   ERROR c.s.m.exception.GlobalExceptionHandler — Gemini API Error: ...
                                                                       // This helps identify exactly where the error was logged.

    /**
     * Handles our custom ApiException (and its subclass ServiceUnavailableException).
     * The HTTP status embedded in the exception controls the response code.
     */
    @ExceptionHandler(ApiException.class)                              // Catches any ApiException thrown by any controller or service.
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.error("API Error [{}]: {}", ex.getStatus(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
            ex.getStatus().value(),                                    // e.g., 503
            ex.getStatus().getReasonPhrase(),                          // e.g., "Service Unavailable"
            ex.getMessage(),                                           // e.g., "Gemini API is currently unavailable"
            Instant.now().toString()
        );
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    /**
     * Handles Jakarta Validation failures — triggered when @Valid on a controller
     * parameter finds constraint violations (e.g., @NotBlank field is empty).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())  // e.g., "prompt: must not be blank"
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");

        log.warn("Validation Error: {}", message);

        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            message,
            Instant.now().toString()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Catch-all handler for any unexpected exception.
     * This prevents stack traces from leaking to the frontend.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error: ", ex);                           // Log the FULL stack trace for debugging (only visible in server logs, not in the API response)

        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred. Please try again later.",    // Intentionally vague — never expose internal details to the client
            Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  HOW SPRING'S EXCEPTION HANDLING PIPELINE WORKS INTERNALLY                 ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  Step 1: A controller method runs and throws an exception.                 ║
 * ║                                                                            ║
 * ║  Step 2: Spring's DispatcherServlet catches the exception BEFORE it        ║
 * ║          reaches the servlet container (Tomcat).                           ║
 * ║                                                                            ║
 * ║  Step 3: Spring checks all @ControllerAdvice classes for an                ║
 * ║          @ExceptionHandler that matches the exception type.                ║
 * ║                                                                            ║
 * ║  Step 4: Spring picks the MOST SPECIFIC handler first.                     ║
 * ║          ServiceUnavailableException extends ApiException extends           ║
 * ║          RuntimeException. So:                                             ║
 * ║          - If there's a handler for ServiceUnavailableException → use it   ║
 * ║          - Else if there's a handler for ApiException → use it             ║
 * ║          - Else if there's a handler for Exception → use it (catch-all)    ║
 * ║                                                                            ║
 * ║  Step 5: The handler method runs and returns a ResponseEntity.             ║
 * ║          Spring serializes the ErrorResponse to JSON (using Jackson)       ║
 * ║          and sends it to the client with the specified HTTP status code.   ║
 * ║                                                                            ║
 * ║  Result: The client always gets a clean JSON response like:                ║
 * ║   {                                                                        ║
 * ║     "status": 503,                                                         ║
 * ║     "error": "Service Unavailable",                                        ║
 * ║     "message": "Gemini API is currently unavailable",                      ║
 * ║     "timestamp": "2026-02-22T10:15:30Z"                                   ║
 * ║   }                                                                        ║
 * ║                                                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
