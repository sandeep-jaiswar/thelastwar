package com.thelastwar.examples;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.List;

/**
 * Example demonstrating SQS operations with LocalStack.
 * Shows how to send, receive, and delete messages.
 */
public class SqsExample {
    
    private final SqsClient sqsClient;
    private final String queueName;
    private String queueUrl;
    
    public SqsExample(String queueName) {
        this.sqsClient = LocalStackConfig.createSqsClient();
        this.queueName = queueName;
        this.queueUrl = getQueueUrl();
    }
    
    /**
     * Gets the queue URL for the given queue name.
     * 
     * @return queue URL
     */
    private String getQueueUrl() {
        try {
            GetQueueUrlResponse response = sqsClient.getQueueUrl(
                GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build()
            );
            return response.queueUrl();
        } catch (QueueDoesNotExistException e) {
            System.err.println("⚠ Queue '" + queueName + "' does not exist. Please run bootstrap script.");
            return null;
        }
    }
    
    /**
     * Sends an order event message to the queue.
     * 
     * @param orderId the order ID
     * @param event the event type
     * @param details event details in JSON format
     * @return message ID
     */
    public String sendOrderEvent(String orderId, String event, String details) {
        if (queueUrl == null) {
            return null;
        }
        
        String messageBody = """
            {
                "orderId": "%s",
                "event": "%s",
                "details": %s,
                "timestamp": "%s"
            }
            """.formatted(orderId, event, details, Instant.now());
        
        SendMessageRequest request = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .messageAttributes(java.util.Map.of(
                "EventType", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(event)
                    .build(),
                "OrderId", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(orderId)
                    .build()
            ))
            .build();
        
        SendMessageResponse response = sqsClient.sendMessage(request);
        System.out.println("✓ Sent message to queue: " + queueName + " (MessageId: " + response.messageId() + ")");
        return response.messageId();
    }
    
    /**
     * Receives messages from the queue.
     * 
     * @param maxMessages maximum number of messages to receive
     * @return list of received messages
     */
    public List<Message> receiveMessages(int maxMessages) {
        if (queueUrl == null) {
            return List.of();
        }
        
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(maxMessages)
            .waitTimeSeconds(5) // Long polling
            .messageAttributeNames("All")
            .build();
        
        ReceiveMessageResponse response = sqsClient.receiveMessage(request);
        List<Message> messages = response.messages();
        
        System.out.println("✓ Received " + messages.size() + " message(s) from queue: " + queueName);
        return messages;
    }
    
    /**
     * Deletes a message from the queue.
     * 
     * @param receiptHandle the message receipt handle
     */
    public void deleteMessage(String receiptHandle) {
        if (queueUrl == null) {
            return;
        }
        
        DeleteMessageRequest request = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build();
        
        sqsClient.deleteMessage(request);
        System.out.println("✓ Deleted message from queue: " + queueName);
    }
    
    /**
     * Purges all messages from the queue (for testing).
     */
    public void purgeQueue() {
        if (queueUrl == null) {
            return;
        }
        
        PurgeQueueRequest request = PurgeQueueRequest.builder()
            .queueUrl(queueUrl)
            .build();
        
        sqsClient.purgeQueue(request);
        System.out.println("✓ Purged all messages from queue: " + queueName);
    }
    
    /**
     * Gets approximate number of messages in the queue.
     * 
     * @return approximate message count
     */
    public int getApproximateMessageCount() {
        if (queueUrl == null) {
            return 0;
        }
        
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
            .build();
        
        GetQueueAttributesResponse response = sqsClient.getQueueAttributes(request);
        return Integer.parseInt(
            response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
        );
    }
    
    /**
     * Demonstration of SQS operations.
     */
    public static void demo() {
        System.out.println("\n========== SQS Example ==========");
        
        SqsExample example = new SqsExample("order-events");
        
        if (example.queueUrl == null) {
            return;
        }
        
        // Send order events
        example.sendOrderEvent("ORD-12345", "ORDER_CREATED", 
            "{\"symbol\":\"AAPL\",\"quantity\":100,\"price\":150.25}");
        example.sendOrderEvent("ORD-12345", "ORDER_FILLED", 
            "{\"filledQuantity\":100,\"avgPrice\":150.28}");
        
        // Check message count
        System.out.println("Approximate messages in queue: " + example.getApproximateMessageCount());
        
        // Receive and process messages
        List<Message> messages = example.receiveMessages(10);
        for (Message message : messages) {
            System.out.println("\nReceived message:");
            System.out.println("  Body: " + message.body().substring(0, Math.min(100, message.body().length())) + "...");
            System.out.println("  Attributes: " + message.messageAttributes().keySet());
            
            // Process message (in real code, do actual processing here)
            
            // Delete message after processing
            example.deleteMessage(message.receiptHandle());
        }
        
        System.out.println("✓ SQS demo completed successfully\n");
    }
    
    public void close() {
        sqsClient.close();
    }
}
