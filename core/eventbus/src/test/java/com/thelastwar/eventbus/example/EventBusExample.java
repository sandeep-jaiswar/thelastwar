package com.thelastwar.eventbus.example;

import com.thelastwar.eventbus.*;

/**
 * Example usage of the Event Bus abstraction.
 * Demonstrates basic publish/subscribe patterns.
 */
public class EventBusExample {

    public static void main(String[] args) {
        // Create and start the event bus
        EventBus eventBus = new InMemoryEventBus();
        eventBus.start();

        // Example 1: Simple subscriber
        System.out.println("=== Example 1: Simple Subscriber ===");
        EventBus.Subscription subscription = eventBus.subscribe(
                EventType.MARKET_DATA_UPDATE,
                event -> System.out.println("Received market data: " + event.payload()));

        // Publish a market data event
        Event marketDataEvent = Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "AAPL: $150.00");
        eventBus.publish(marketDataEvent);

        // Example 2: Multiple subscribers to same event type
        System.out.println("\n=== Example 2: Multiple Subscribers ===");
        eventBus.subscribe(
                EventType.ORDER_FILLED,
                event -> System.out.println("Risk Manager: Order filled - " + event.payload()));

        eventBus.subscribe(
                EventType.ORDER_FILLED,
                event -> System.out.println("Analytics: Recording trade - " + event.payload()));

        Event orderFilledEvent = Event.create(
                System.nanoTime(),
                2L,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED,
                0L,
                "Order #12345 filled @ $150.00");
        eventBus.publish(orderFilledEvent);

        // Example 3: Error handling
        System.out.println("\n=== Example 3: Error Handling ===");
        EventHandler faultyHandler = new EventHandler() {
            @Override
            public void onEvent(Event event) {
                throw new RuntimeException("Simulated processing error");
            }

            @Override
            public void onError(Event event, Throwable exception) {
                System.out.println("Error handler: " + exception.getMessage());
            }
        };

        eventBus.subscribe(EventType.RISK_CHECK_FAILED, faultyHandler);

        Event riskEvent = Event.create(
                System.nanoTime(),
                3L,
                SourceId.RISK_MANAGER,
                EventType.RISK_CHECK_FAILED,
                0L,
                "Position limit exceeded");
        eventBus.publish(riskEvent);

        // Example 4: Unsubscribe
        System.out.println("\n=== Example 4: Unsubscribe ===");
        System.out.println("Subscriber count before unsubscribe: " +
                eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE));
        subscription.unsubscribe();
        System.out.println("Subscriber count after unsubscribe: " +
                eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE));

        // Example 5: Performance metrics
        System.out.println("\n=== Example 5: Performance Metrics ===");
        System.out.println("Total events published: " + eventBus.getPublishedEventCount());

        // Cleanup
        eventBus.stop();
        System.out.println("\nEvent bus stopped.");
    }
}
