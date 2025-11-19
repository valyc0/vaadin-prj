package com.example.common.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for file information stored in MinIO
 */
public class FileInfoDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String fileName;
    private String contentType;
    private Long size;
    private String lastModified;
    private String etag;
    
    public FileInfoDTO() {
    }
    
    public FileInfoDTO(String fileName, String contentType, Long size, String lastModified, String etag) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.lastModified = lastModified;
        this.etag = etag;
    }
    
    // Getters and Setters
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public Long getSize() {
        return size;
    }
    
    public void setSize(Long size) {
        this.size = size;
    }
    
    public String getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
    
    public String getEtag() {
        return etag;
    }
    
    public void setEtag(String etag) {
        this.etag = etag;
    }
    
    /**
     * Get formatted file size
     */
    public String getFormattedSize() {
        if (size == null) {
            return "N/A";
        }
        
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        return "FileInfoDTO{" +
                "fileName='" + fileName + '\'' +
                ", contentType='" + contentType + '\'' +
                ", size=" + size +
                ", lastModified='" + lastModified + '\'' +
                ", etag='" + etag + '\'' +
                '}';
    }
}
