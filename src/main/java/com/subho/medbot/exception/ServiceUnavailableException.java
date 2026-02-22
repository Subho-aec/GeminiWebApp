package com.subho.medbot.exception;

/**
 * Thrown when an external service (Gemini API, AssemblyAI, etc.) is unreachable
 * or returns a 5xx server error.
 *
 * This is a specific subclass of ApiException with a fixed 503 SERVICE_UNAVAILABLE status.
 * Having a dedicated exception class (rather than always using ApiException) lets us:
 * 1. Catch and handle external-service failures differently from input validation errors
 * 2. Add automatic retry logic specifically for this exception type in the future
 * 3. Track and alert on service unavailability separately in monitoring dashboards
 */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String serviceName) {
        super(serviceName + " is currently unavailable. Please try again later.",
              org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String serviceName, Throwable cause) {
        super(serviceName + " is currently unavailable: " + cause.getMessage(),
              org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
