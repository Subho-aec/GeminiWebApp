package com.subho.medbot.exception;

import org.springframework.http.HttpStatus;                            // Spring's enum that maps to HTTP status codes (200 OK, 400 Bad Request, 500 Internal Server Error, etc.).
                                                                       // By embedding the status in the exception itself, our GlobalExceptionHandler can read it
                                                                       // and return the correct HTTP response code automatically.

/**
 * A general-purpose exception for API errors in MedBot.
 *
 * Instead of throwing raw RuntimeException with just a message string, this exception
 * carries an HttpStatus so the GlobalExceptionHandler can automatically set the correct
 * HTTP response code. This is a standard enterprise Java pattern.
 *
 * Usage example:
 *   throw new ApiException("Gemini API key is invalid", HttpStatus.UNAUTHORIZED);
 *   → produces a 401 response with a structured JSON error body.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;                                   // The HTTP status code that should be returned to the client.
                                                                       // Using HttpStatus (an enum) instead of a raw int like 400 prevents invalid codes
                                                                       // and makes the code self-documenting. You can immediately see it's a BAD_REQUEST
                                                                       // rather than guessing what 400 means.

    public ApiException(String message, HttpStatus status) {
        super(message);                                                // Pass the human-readable error message to RuntimeException's constructor
        this.status = status;
    }

    public ApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);                                         // The "cause" preserves the original exception's stack trace for debugging.
                                                                       // Example: if Gemini API throws a HttpClientErrorException, we wrap it in ApiException
                                                                       // but keep the original so logs show the full chain of what went wrong.
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
