package com.thelastwar.examples;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;

/**
 * Example demonstrating S3 operations with LocalStack.
 * Shows how to upload, download, list, and delete objects.
 */
public class S3Example {
    
    private final S3Client s3Client;
    private final String bucketName;
    
    public S3Example(String bucketName) {
        this.s3Client = LocalStackConfig.createS3Client();
        this.bucketName = bucketName;
    }
    
    /**
     * Uploads an order snapshot to S3.
     * 
     * @param orderId the order ID
     * @param snapshotData the snapshot data as JSON
     * @return the S3 object key
     */
    public String uploadOrderSnapshot(String orderId, String snapshotData) {
        String key = String.format("snapshots/%s/%s.json", 
            orderId, Instant.now().toEpochMilli());
        
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("application/json")
            .metadata(java.util.Map.of(
                "orderId", orderId,
                "timestamp", Instant.now().toString()
            ))
            .build();
        
        s3Client.putObject(request, RequestBody.fromString(snapshotData));
        
        System.out.println("✓ Uploaded order snapshot to s3://" + bucketName + "/" + key);
        return key;
    }
    
    /**
     * Downloads an object from S3.
     * 
     * @param key the S3 object key
     * @return the object content as a string
     */
    public String downloadObject(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
        
        String content = s3Client.getObjectAsBytes(request).asUtf8String();
        System.out.println("✓ Downloaded object from s3://" + bucketName + "/" + key);
        return content;
    }
    
    /**
     * Lists all objects in the bucket with a given prefix.
     * 
     * @param prefix the key prefix
     */
    public void listObjects(String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(prefix)
            .maxKeys(10)
            .build();
        
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        
        System.out.println("\nObjects in s3://" + bucketName + "/" + prefix + ":");
        response.contents().forEach(obj -> {
            System.out.printf("  - %s (size: %d bytes, modified: %s)%n",
                obj.key(), obj.size(), obj.lastModified());
        });
    }
    
    /**
     * Deletes an object from S3.
     * 
     * @param key the S3 object key
     */
    public void deleteObject(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
        
        s3Client.deleteObject(request);
        System.out.println("✓ Deleted object s3://" + bucketName + "/" + key);
    }
    
    /**
     * Checks if a bucket exists.
     * 
     * @param bucketName the bucket name
     * @return true if bucket exists, false otherwise
     */
    public boolean bucketExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(bucketName)
                .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Demonstration of S3 operations.
     */
    public static void demo() {
        System.out.println("\n========== S3 Example ==========");
        
        S3Example example = new S3Example("trading-orders");
        
        // Check bucket exists
        if (!example.bucketExists("trading-orders")) {
            System.out.println("⚠ Bucket 'trading-orders' does not exist. Please run bootstrap script.");
            return;
        }
        
        // Upload order snapshot
        String orderSnapshot = """
            {
                "orderId": "ORD-12345",
                "symbol": "AAPL",
                "side": "BUY",
                "quantity": 100,
                "price": 150.25,
                "status": "FILLED",
                "timestamp": "%s"
            }
            """.formatted(Instant.now());
        
        String key = example.uploadOrderSnapshot("ORD-12345", orderSnapshot);
        
        // Download the snapshot
        String downloaded = example.downloadObject(key);
        System.out.println("Downloaded content: " + downloaded.substring(0, Math.min(100, downloaded.length())) + "...");
        
        // List objects
        example.listObjects("snapshots/");
        
        // Clean up - delete the test object
        example.deleteObject(key);
        
        System.out.println("✓ S3 demo completed successfully\n");
    }
    
    public void close() {
        s3Client.close();
    }
}
