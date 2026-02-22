package com.subho.medbot.dto.response;

/**
 * Structured error response following the RFC 7807 "Problem Details" spirit.
 * Every error from MedBot returns this same shape, making it easy for the frontend
 * to parse and display errors consistently.
 */
public class ErrorResponse {

    private int status;                                                // HTTP status code (e.g., 400, 503, 500) — duplicated in the body for convenience,
                                                                       // since some HTTP clients make it hard to read the response status separately.
    private String error;                                              // Human-readable status phrase (e.g., "Bad Request", "Service Unavailable").
    private String message;                                            // Detailed error description (e.g., "Prompt cannot be empty").
    private String timestamp;                                          // ISO-8601 timestamp when the error occurred. Helps with debugging and correlating
                                                                       // errors in logs across different time zones.

    public ErrorResponse() {}

    public ErrorResponse(int status, String error, String message, String timestamp) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
    }

    public int getStatus()          { return status; }
    public String getError()        { return error; }
    public String getMessage()      { return message; }
    public String getTimestamp()     { return timestamp; }
}
