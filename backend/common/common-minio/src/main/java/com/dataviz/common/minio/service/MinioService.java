package com.dataviz.common.minio.service;

import com.dataviz.common.minio.model.FileUploadResult;
import com.dataviz.common.minio.properties.MinioProperties;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MinIO Object Storage Service
 */
@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * Upload an object to the specified bucket
     */
    public FileUploadResult upload(String bucket, String objectName, InputStream inputStream,
                                    long size, String contentType) {
        try {
            ensureBucketExists(bucket);
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build();
            ObjectWriteResponse response = minioClient.putObject(args);
            String url = buildFileUrl(bucket, objectName);
            log.info("Uploaded object={} to bucket={}, etag={}", objectName, bucket, response.etag());
            return new FileUploadResult(objectName, url, objectName, size, contentType, response.etag());
        } catch (Exception e) {
            log.error("Upload failed bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("MinIO upload failed", e);
        }
    }

    /**
     * Upload with auto-size detection (buffered, part size -1)
     */
    public FileUploadResult upload(String bucket, String objectName, InputStream inputStream,
                                    String contentType) {
        try {
            return upload(bucket, objectName, inputStream, -1, contentType);
        } catch (Exception e) {
            throw new RuntimeException("MinIO upload failed", e);
        }
    }

    /**
     * Multipart upload - upload in parts of specified size
     */
    public FileUploadResult uploadMultipart(String bucket, String objectName, InputStream inputStream,
                                             long size, String contentType, int partSize) {
        try {
            ensureBucketExists(bucket);
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, partSize)
                    .contentType(contentType)
                    .build();
            ObjectWriteResponse response = minioClient.putObject(args);
            String url = buildFileUrl(bucket, objectName);
            log.info("Multipart uploaded object={} to bucket={}, partSize={}", objectName, bucket, partSize);
            return new FileUploadResult(objectName, url, objectName, size, contentType, response.etag());
        } catch (Exception e) {
            log.error("Multipart upload failed bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("MinIO multipart upload failed", e);
        }
    }

    /**
     * Download an object as InputStream
     */
    public InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("Download failed bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("MinIO download failed", e);
        }
    }

    /**
     * Get a presigned URL for temporary access
     */
    public String getPresignedUrl(String bucket, String objectName, int expiry, TimeUnit unit) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry((int) unit.toSeconds(expiry))
                    .build());
        } catch (Exception e) {
            log.error("Presigned URL generation failed bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("MinIO presigned URL failed", e);
        }
    }

    /**
     * Get a presigned URL with default 1-hour expiry
     */
    public String getPresignedUrl(String bucket, String objectName) {
        return getPresignedUrl(bucket, objectName, 1, TimeUnit.HOURS);
    }

    /**
     * Delete an object
     */
    public void delete(String bucket, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            log.info("Deleted object={} from bucket={}", objectName, bucket);
        } catch (Exception e) {
            log.error("Delete failed bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("MinIO delete failed", e);
        }
    }

    /**
     * Batch delete multiple objects
     */
    public List<DeleteError> deleteObjects(String bucket, List<String> objectNames) {
        try {
            List<DeleteObject> objects = objectNames.stream()
                    .map(DeleteObject::new)
                    .collect(Collectors.toList());
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(bucket)
                    .objects(objects)
                    .build());
            List<DeleteError> errors = new ArrayList<>();
            for (Result<DeleteError> result : results) {
                errors.add(result.get());
            }
            return errors;
        } catch (Exception e) {
            log.error("Batch delete failed bucket={}", bucket, e);
            throw new RuntimeException("MinIO batch delete failed", e);
        }
    }

    /**
     * List objects in a bucket with optional prefix
     */
    public List<Item> listObjects(String bucket, String prefix, boolean recursive) {
        try {
            List<Item> items = new ArrayList<>();
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(recursive)
                    .build());
            for (Result<Item> result : results) {
                items.add(result.get());
            }
            return items;
        } catch (Exception e) {
            log.error("List objects failed bucket={}, prefix={}", bucket, prefix, e);
            throw new RuntimeException("MinIO list objects failed", e);
        }
    }

    /**
     * Check if a bucket exists
     */
    public boolean bucketExists(String bucket) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.error("Bucket exists check failed bucket={}", bucket, e);
            throw new RuntimeException("MinIO bucket check failed", e);
        }
    }

    /**
     * Create a bucket if it does not exist
     */
    public void createBucket(String bucket) {
        try {
            if (!bucketExists(bucket)) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created bucket={}", bucket);
            }
        } catch (Exception e) {
            log.error("Create bucket failed bucket={}", bucket, e);
            throw new RuntimeException("MinIO create bucket failed", e);
        }
    }

    private void ensureBucketExists(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            bucket = properties.getBucketName();
        }
        createBucket(bucket);
    }

    private String buildFileUrl(String bucket, String objectName) {
        return properties.getEndpoint() + "/" + bucket + "/" + objectName;
    }
}
