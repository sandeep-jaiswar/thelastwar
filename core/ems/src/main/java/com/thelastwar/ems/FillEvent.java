package com.thelastwar.ems;

import com.thelastwar.oms.InternalOrderId;

import java.time.Instant;

/**
 * FillEvent represents a fill notification for a child order.
 * 
 * This immutable record is passed to strategies to inform them of execution progress.
 */
public record FillEvent(
    /** ID of the child order that was filled */
    InternalOrderId childOrderId,
    
    /** ID of the parent order */
    InternalOrderId parentOrderId,
    
    /** Timestamp of the fill */
    Instant timestamp,
    
    /** Fill price */
    long fillPrice,
    
    /** Fill quantity */
    long fillQuantity,
    
    /** Remaining quantity on the child order */
    long remainingQuantity,
    
    /** Venue where the fill occurred */
    String venue,
    
    /** Execution ID */
    String executionId
) {
    
    /**
     * Check if this fill completely filled the child order.
     * 
     * @return true if child order is fully filled
     */
    public boolean isFullFill() {
        return remainingQuantity == 0;
    }
    
    /**
     * Check if this is a partial fill.
     * 
     * @return true if child order is partially filled
     */
    public boolean isPartialFill() {
        return remainingQuantity > 0;
    }
}
