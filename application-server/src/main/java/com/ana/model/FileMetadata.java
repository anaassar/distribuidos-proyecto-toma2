package com.ana.model;

import java.io.Serializable;

public class FileMetadata implements Serializable {
    private String id;
    private String name;
    private String path;
    private long sizeBytes;
    private int ownerId;
    private Integer directoryId;

    // Constructors
    public FileMetadata() {}
    
    public FileMetadata(String path, long sizeBytes, int ownerId) {
        this.name = extractFileName(path);
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.ownerId = ownerId;
    }
    
    private String extractFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
    public Integer getDirectoryId() { return directoryId; }
    public void setDirectoryId(Integer directoryId) { this.directoryId = directoryId; }
}