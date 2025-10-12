package com.thelastwar.eventbus.example;

import com.thelastwar.eventbus.AeronExecutionPublisher;
import com.thelastwar.eventbus.ExecutionPublisher;
import com.thelastwar.eventbus.model.EventSerializer;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.eventbus.model.TradeEvent;
import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration example demonstrating ExecutionPublisher with actual Aeron subscribers.
 * 
 * This example shows:
 * 1. Creating an ExecutionPublisher
 * 2. Setting up downstream subscribers for positions, ledger, and executions
 * 3. Publishing execution and trade events
 * 4. Receiving and deserializing events on the subscriber side
 * 5. Measuring end-to-end latency
 * 
 * Run this example to verify the complete event flow from publisher to subscribers.
 */
public class ExecutionPublisherIntegrationExample {
    
    private static final String IPC_CHANNEL = "aeron:ipc";
    private static final int STREAM_ID = 4001;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== ExecutionPublisher Integration Example ===\n");
        
        // Counters for received events
        AtomicInteger executionsReceived = new AtomicInteger(0);
        AtomicInteger tradesReceived = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2); // Wait for 1 execution + 1 trade
        
        // Setup subscribers first
        MediaDriver subscriberMediaDriver = MediaDriver.launch(new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .aeronDirectoryName(System.getProperty("java.io.tmpdir") + "/aeron-sub-" + System.nanoTime())
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
        
        Aeron subscriberAeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(subscriberMediaDriver.aeronDirectoryName()));
        
        // Subscribe to executions channel (positions.in or executions.out)
        Subscription executionsSub = subscriberAeron.addSubscription(IPC_CHANNEL, STREAM_ID);
        
        // Subscribe to ledger channel (ledger.in)
        Subscription ledgerSub = subscriberAeron.addSubscription(IPC_CHANNEL, STREAM_ID);
        
        // Fragment handlers for deserialization
        FragmentHandler executionHandler = new FragmentAssembler((buffer, offset, length, header) -> {
            try {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer.byteArray(), offset, length);
                ExecutionEvent event = EventSerializer.deserializeExecution(byteBuffer);
                System.out.println("Received Execution: " + event);
                executionsReceived.incrementAndGet();
                latch.countDown();
            } catch (Exception e) {
                System.err.println("Error deserializing execution: " + e.getMessage());
            }
        });
        
        FragmentHandler tradeHandler = new FragmentAssembler((buffer, offset, length, header) -> {
            try {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer.byteArray(), offset, length);
                TradeEvent event = EventSerializer.deserializeTrade(byteBuffer);
                System.out.println("Received Trade: " + event);
                tradesReceived.incrementAndGet();
                latch.countDown();
            } catch (Exception e) {
                System.err.println("Error deserializing trade: " + e.getMessage());
            }
        });
        
        // Start polling thread for subscribers
        Thread pollingThread = new Thread(() -> {
            IdleStrategy idleStrategy = new SleepingIdleStrategy(1000);
            while (!Thread.currentThread().isInterrupted()) {
                int fragments = executionsSub.poll(executionHandler, 10);
                fragments += ledgerSub.poll(tradeHandler, 10);
                idleStrategy.idle(fragments);
            }
        });
        pollingThread.start();
        
        // Give subscribers time to connect
        Thread.sleep(500);
        
        // Create and start publisher
        System.out.println("Starting ExecutionPublisher...");
        ExecutionPublisher publisher = AeronExecutionPublisher.builder()
                .positionsChannel(IPC_CHANNEL)
                .ledgerChannel(IPC_CHANNEL)
                .executionsChannel(IPC_CHANNEL)
                .streamId(STREAM_ID)
                .metricsEnabled(true)
                .build();
        
        publisher.start();
        System.out.println("Publisher started\n");
        
        // Create test events
        OrderEvent order = OrderEvent.newOrder(
                12345L,
                "AAPL",
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15050L, // $150.50
                999L,
                1
        );
        
        ExecutionEvent execution = ExecutionEvent.fill(
                1L,
                order,
                100L,
                15050L,
                100L,
                0L
        );
        
        TradeEvent trade = new TradeEvent(
                1L,
                12345L,
                "AAPL",
                TradeEvent.SIDE_BUY,
                100L,
                15050L,
                System.nanoTime(),
                999L,
                1,
                54321L, // counterparty
                10L     // fees
        );
        
        // Publish events
        System.out.println("Publishing execution event...");
        long publishStart = System.nanoTime();
        boolean execPublished = publisher.publishExecution(execution);
        long publishDuration = System.nanoTime() - publishStart;
        
        System.out.println("Execution published: " + execPublished + 
                          " (took " + publishDuration + " ns)");
        
        System.out.println("\nPublishing trade event...");
        publishStart = System.nanoTime();
        boolean tradePublished = publisher.publishTrade(trade);
        publishDuration = System.nanoTime() - publishStart;
        
        System.out.println("Trade published: " + tradePublished + 
                          " (took " + publishDuration + " ns)");
        
        // Wait for events to be received
        System.out.println("\nWaiting for subscribers to receive events...");
        boolean received = latch.await(5, TimeUnit.SECONDS);
        
        // Print statistics
        System.out.println("\n=== Statistics ===");
        System.out.println("Executions received: " + executionsReceived.get());
        System.out.println("Trades received: " + tradesReceived.get());
        System.out.println("Publisher stats:");
        System.out.println("  - Executions published: " + publisher.getPublishedExecutionCount());
        System.out.println("  - Trades published: " + publisher.getPublishedTradeCount());
        System.out.println("  - Back-pressure count: " + publisher.getBackPressureCount());
        
        if (received) {
            System.out.println("\n✓ All events received successfully!");
        } else {
            System.out.println("\n✗ Timeout waiting for events");
        }
        
        // Cleanup
        pollingThread.interrupt();
        pollingThread.join(1000);
        publisher.stop();
        executionsSub.close();
        ledgerSub.close();
        subscriberAeron.close();
        subscriberMediaDriver.close();
        
        System.out.println("\n=== Example completed ===");
    }
}
