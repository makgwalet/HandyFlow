package za.co.handyflow.platform.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * WHY A UNIFORM API RESPONSE WRAPPER?
 *
 * Consistency is key in APIs. Every endpoint returns the same shape:
 * {
 *   "success": true,
 *   "message": "User created successfully",
 *   "data": { ... },
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 *
 * Benefits:
 * 1. Frontend knows exactly what to expect
 * 2. Easier error handling on the client
 * 3. Audit trail (timestamp on every response)
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // WHY: Don't serialize null fields
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final Instant timestamp;


    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }}
