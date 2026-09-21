package com.dataviz.common.minio.model;

import java.io.Serializable;

/**
 * Result object returned after a successful file upload
 */
public class FileUploadResult implements Serializable {

    /** Original file name */
    private String fileName;

    /** Full accessible URL of the uploaded object */
    private String fileUrl;

    /** Object name (key) in the bucket */
    private String objectName;

    /** File size in bytes */
    private long size;

    /** MIME content type */
    private String contentType;

    /** ETag returned by MinIO */
    private String etag;

    public FileUploadResult() {}

    public FileUploadResult(String fileName, String fileUrl, String objectName,
                            long size, String contentType, String etag) {
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.objectName = objectName;
        this.size = size;
        this.contentType = contentType;
        this.etag = etag;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }

    @Override
    public String toString() {
        return "FileUploadResult{fileName='" + fileName + "', objectName='" + objectName +
                "', size=" + size + ", contentType='" + contentType + "'}";
    }
}
