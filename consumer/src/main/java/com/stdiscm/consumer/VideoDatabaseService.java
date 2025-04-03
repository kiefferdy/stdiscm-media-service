package com.stdiscm.consumer;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoDatabaseService {
    
    private final Map<String, VideoMetadata> videos = new ConcurrentHashMap<>();
    
    public void addVideo(VideoMetadata video) {
        videos.put(video.getId(), video);
    }
    
    public VideoMetadata getVideo(String id) {
        return videos.get(id);
    }
    
    public List<VideoMetadata> getAllVideos() {
        List<VideoMetadata> videoList = new ArrayList<>(videos.values());
        // Sort by upload time (most recent first)
        Collections.sort(videoList, (v1, v2) -> v2.getUploadTime().compareTo(v1.getUploadTime()));
        return videoList;
    }
    
    public boolean hasVideo(String id) {
        return videos.containsKey(id);
    }
}
