package com.stdiscm.consumer;

import java.time.LocalDateTime;

public class VideoFile {
    private final String fileName;
    private final String fileHash;
    private final byte[] fileData;
    private final LocalDateTime uploadTime;
    
    public VideoFile(String fileName, String fileHash, byte[] fileData) {
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.fileData = fileData;
        this.uploadTime = LocalDateTime.now();
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getFileHash() {
        return fileHash;
    }
    
    public byte[] getFileData() {
        return fileData;
    }
    
    public LocalDateTime getUploadTime() {
        return uploadTime;
    }
}
