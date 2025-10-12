package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for order cancellation requests.
 */
@CompiledJson
public record OrderCancelRequest(
    String clientOrderId
) {
    /**
     * Validates the cancellation request.
     * 
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (clientOrderId == null || clientOrderId.isEmpty()) {
            throw new IllegalArgumentException("Client order ID is required");
        }
    }
}
