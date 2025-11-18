package com.thelastwar.examples;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Configuration class for AWS SDK clients targeting LocalStack.
 * This configuration allows seamless switching between LocalStack and real AWS.
 */
public class LocalStackConfig {
    
    private static final String LOCALSTACK_ENDPOINT = 
        System.getenv().getOrDefault("LOCALSTACK_ENDPOINT", "http://localhost:4566");
    
    private static final Region REGION = Region.US_EAST_1;
    
    private static final StaticCredentialsProvider CREDENTIALS = 
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test", "test")
        );
    
    private static final boolean USE_LOCALSTACK = 
        Boolean.parseBoolean(System.getenv().getOrDefault("USE_LOCALSTACK", "true"));
    
    /**
     * Creates an S3 client configured for LocalStack.
     * 
     * @return configured S3Client
     */
    public static S3Client createS3Client() {
        var builder = S3Client.builder()
            .region(REGION)
            .credentialsProvider(CREDENTIALS);
        
        if (USE_LOCALSTACK) {
            builder.endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                   .forcePathStyle(true); // Required for LocalStack S3
        }
        
        return builder.build();
    }
    
    /**
     * Creates an SQS client configured for LocalStack.
     * 
     * @return configured SqsClient
     */
    public static SqsClient createSqsClient() {
        var builder = SqsClient.builder()
            .region(REGION)
            .credentialsProvider(CREDENTIALS);
        
        if (USE_LOCALSTACK) {
            builder.endpointOverride(URI.create(LOCALSTACK_ENDPOINT));
        }
        
        return builder.build();
    }
    
    /**
     * Creates an SNS client configured for LocalStack.
     * 
     * @return configured SnsClient
     */
    public static SnsClient createSnsClient() {
        var builder = SnsClient.builder()
            .region(REGION)
            .credentialsProvider(CREDENTIALS);
        
        if (USE_LOCALSTACK) {
            builder.endpointOverride(URI.create(LOCALSTACK_ENDPOINT));
        }
        
        return builder.build();
    }
    
    /**
     * Creates a Secrets Manager client configured for LocalStack.
     * 
     * @return configured SecretsManagerClient
     */
    public static SecretsManagerClient createSecretsManagerClient() {
        var builder = SecretsManagerClient.builder()
            .region(REGION)
            .credentialsProvider(CREDENTIALS);
        
        if (USE_LOCALSTACK) {
            builder.endpointOverride(URI.create(LOCALSTACK_ENDPOINT));
        }
        
        return builder.build();
    }
    
    /**
     * Creates a DynamoDB client configured for LocalStack.
     * 
     * @return configured DynamoDbClient
     */
    public static DynamoDbClient createDynamoDbClient() {
        var builder = DynamoDbClient.builder()
            .region(REGION)
            .credentialsProvider(CREDENTIALS);
        
        if (USE_LOCALSTACK) {
            builder.endpointOverride(URI.create(LOCALSTACK_ENDPOINT));
        }
        
        return builder.build();
    }
    
    /**
     * Checks if LocalStack mode is enabled.
     * 
     * @return true if using LocalStack, false otherwise
     */
    public static boolean isLocalStackEnabled() {
        return USE_LOCALSTACK;
    }
    
    /**
     * Gets the LocalStack endpoint URL.
     * 
     * @return endpoint URL
     */
    public static String getEndpoint() {
        return LOCALSTACK_ENDPOINT;
    }
}
