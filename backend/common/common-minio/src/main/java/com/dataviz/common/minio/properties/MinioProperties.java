package com.dataviz.common.minio.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO connection properties bound from application.yml under prefix "minio"
 */
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO server endpoint, e.g. http://localhost:9000 */
    private String endpoint = "http://localhost:9000";

    /** Access key (username) */
    private String accessKey;

    /** Secret key (password) */
    private String secretKey;

    /** Default bucket name */
    private String bucketName = "default";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
}
