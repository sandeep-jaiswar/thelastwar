package com.thelastwar.eventbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTypeTest {

    @Test
    void testFeedHandlerEventTypes() {
        assertTrue(EventType.isValid(EventType.MARKET_DATA_UPDATE));
        assertTrue(EventType.isValid(EventType.MARKET_DATA_SNAPSHOT));
        assertTrue(EventType.isValid(EventType.FEED_CONNECTION_STATUS));
        assertEquals("FeedHandler", EventType.getSubsystem(EventType.MARKET_DATA_UPDATE));
    }

    @Test
    void testMatchingEngineEventTypes() {
        assertTrue(EventType.isValid(EventType.ORDER_ACCEPTED));
        assertTrue(EventType.isValid(EventType.ORDER_REJECTED));
        assertTrue(EventType.isValid(EventType.ORDER_FILLED));
        assertTrue(EventType.isValid(EventType.ORDER_PARTIALLY_FILLED));
        assertTrue(EventType.isValid(EventType.ORDER_CANCELLED));
        assertEquals("MatchingEngine", EventType.getSubsystem(EventType.ORDER_FILLED));
    }

    @Test
    void testRiskManagementEventTypes() {
        assertTrue(EventType.isValid(EventType.RISK_CHECK_PASSED));
        assertTrue(EventType.isValid(EventType.RISK_CHECK_FAILED));
        assertTrue(EventType.isValid(EventType.POSITION_UPDATE));
        assertTrue(EventType.isValid(EventType.LIMIT_BREACH));
        assertEquals("Risk", EventType.getSubsystem(EventType.RISK_CHECK_PASSED));
    }

    @Test
    void testOMSEventTypes() {
        assertTrue(EventType.isValid(EventType.ORDER_SUBMITTED));
        assertTrue(EventType.isValid(EventType.ORDER_MODIFIED));
        assertTrue(EventType.isValid(EventType.ORDER_STATUS_UPDATE));
        assertEquals("OMS", EventType.getSubsystem(EventType.ORDER_SUBMITTED));
    }

    @Test
    void testAnalyticsEventTypes() {
        assertTrue(EventType.isValid(EventType.PERFORMANCE_METRIC));
        assertTrue(EventType.isValid(EventType.LATENCY_SAMPLE));
        assertTrue(EventType.isValid(EventType.TRADE_ANALYTICS));
        assertEquals("Analytics", EventType.getSubsystem(EventType.LATENCY_SAMPLE));
    }

    @Test
    void testSystemEventTypes() {
        assertTrue(EventType.isValid(EventType.SYSTEM_STARTUP));
        assertTrue(EventType.isValid(EventType.SYSTEM_SHUTDOWN));
        assertTrue(EventType.isValid(EventType.HEARTBEAT));
        assertEquals("System", EventType.getSubsystem(EventType.HEARTBEAT));
    }

    @Test
    void testInvalidEventTypes() {
        assertFalse(EventType.isValid(0));
        assertFalse(EventType.isValid(999));
        assertFalse(EventType.isValid(6000)); // Outside Analytics range
        assertFalse(EventType.isValid(10000));
        assertFalse(EventType.isValid(-1));
    }

    @Test
    void testUnknownSubsystem() {
        assertEquals("Unknown", EventType.getSubsystem(999));
        assertEquals("Unknown", EventType.getSubsystem(-1));
    }

    @Test
    void testEventTypesAreIntegers() {
        // Verify no autoboxing by checking types are primitive int constants
        int marketData = EventType.MARKET_DATA_UPDATE;
        int orderFilled = EventType.ORDER_FILLED;
        
        assertTrue(marketData > 0);
        assertTrue(orderFilled > 0);
        assertNotEquals(marketData, orderFilled);
    }
}
