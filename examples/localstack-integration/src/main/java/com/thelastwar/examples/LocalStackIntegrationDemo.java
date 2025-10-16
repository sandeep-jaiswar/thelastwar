package com.thelastwar.examples;

/**
 * Main demonstration class for LocalStack integration.
 * Runs examples for S3, SQS, and other AWS services with LocalStack.
 */
public class LocalStackIntegrationDemo {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(50));
        System.out.println("LocalStack Integration Demo");
        System.out.println("=".repeat(50));
        System.out.println();
        
        if (!LocalStackConfig.isLocalStackEnabled()) {
            System.err.println("⚠ LocalStack is not enabled. Set USE_LOCALSTACK=true");
            System.exit(1);
        }
        
        System.out.println("LocalStack Endpoint: " + LocalStackConfig.getEndpoint());
        System.out.println("Region: US_EAST_1");
        System.out.println();
        
        try {
            // Run S3 demo
            S3Example.demo();
            
            // Run SQS demo
            SqsExample.demo();
            
            System.out.println("=".repeat(50));
            System.out.println("✓ All demos completed successfully!");
            System.out.println("=".repeat(50));
            
        } catch (Exception e) {
            System.err.println("\n✗ Demo failed with error:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
