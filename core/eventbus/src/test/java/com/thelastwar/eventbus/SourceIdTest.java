package com.thelastwar.eventbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceIdTest {

    @Test
    void testSourceIdConstants() {
        assertEquals(1, SourceId.FEED_HANDLER);
        assertEquals(2, SourceId.MATCHING_ENGINE);
        assertEquals(3, SourceId.RISK_MANAGER);
        assertEquals(4, SourceId.OMS);
        assertEquals(5, SourceId.ANALYTICS);
        assertEquals(99, SourceId.SYSTEM);
    }

    @Test
    void testGetName() {
        assertEquals("FeedHandler", SourceId.getName(SourceId.FEED_HANDLER));
        assertEquals("MatchingEngine", SourceId.getName(SourceId.MATCHING_ENGINE));
        assertEquals("RiskManager", SourceId.getName(SourceId.RISK_MANAGER));
        assertEquals("OMS", SourceId.getName(SourceId.OMS));
        assertEquals("Analytics", SourceId.getName(SourceId.ANALYTICS));
        assertEquals("System", SourceId.getName(SourceId.SYSTEM));
    }

    @Test
    void testGetNameForUnknownSource() {
        assertEquals("Unknown", SourceId.getName(999));
        assertEquals("Unknown", SourceId.getName(-1));
        assertEquals("Unknown", SourceId.getName(0));
    }

    @Test
    void testSourceIdsAreUnique() {
        int[] sourceIds = {
            SourceId.FEED_HANDLER,
            SourceId.MATCHING_ENGINE,
            SourceId.RISK_MANAGER,
            SourceId.OMS,
            SourceId.ANALYTICS,
            SourceId.SYSTEM
        };

        for (int i = 0; i < sourceIds.length; i++) {
            for (int j = i + 1; j < sourceIds.length; j++) {
                assertNotEquals(sourceIds[i], sourceIds[j], 
                    "Source IDs must be unique: " + sourceIds[i] + " vs " + sourceIds[j]);
            }
        }
    }
}
