package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for API responses.
 */
@CompiledJson
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    long timestamp
) {
    /**
     * Creates a successful response.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, System.currentTimeMillis());
    }
    
    /**
     * Creates a successful response with a message.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, System.currentTimeMillis());
    }
    
    /**
     * Creates an error response.
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, System.currentTimeMillis());
    }
}
