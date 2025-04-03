package com.stdiscm.consumer;

import java.time.LocalDateTime;

public class VideoMetadata {
    private final String id;          // Using hash as ID
    private final String fileName;
    private final String filePath;
    private final String thumbnailPath;
    private final String previewPath;
    private final long fileSize;
    private final LocalDateTime uploadTime;
    
    public VideoMetadata(String id, String fileName, String filePath, String thumbnailPath, 
                        String previewPath, long fileSize, LocalDateTime uploadTime) {
        this.id = id;
        this.fileName = fileName;
        this.filePath = filePath;
        this.thumbnailPath = thumbnailPath;
        this.previewPath = previewPath;
        this.fileSize = fileSize;
        this.uploadTime = uploadTime;
    }
    
    public String getId() {
        return id;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public String getThumbnailPath() {
        return thumbnailPath;
    }
    
    public String getPreviewPath() {
        return previewPath;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public LocalDateTime getUploadTime() {
        return uploadTime;
    }
    
    public String getRelativeFilePath() {
        return filePath.substring(filePath.lastIndexOf("uploads"));
    }
    
    public String getRelativeThumbnailPath() {
        return thumbnailPath.substring(thumbnailPath.lastIndexOf("uploads"));
    }
}
