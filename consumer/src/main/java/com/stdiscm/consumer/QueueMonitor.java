package com.stdiscm.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Component
@EnableScheduling
public class QueueMonitor {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private BlockingQueue<VideoFile> videoQueue;
    private int maxQueueSize;

    public void initialize(BlockingQueue<VideoFile> videoQueue, int maxQueueSize) {
        this.videoQueue = videoQueue;
        this.maxQueueSize = maxQueueSize;
    }

    @Scheduled(fixedRate = 5000) // Report every 5 seconds
    public void reportQueueStatus() {
        if (videoQueue != null) {
            Map<String, Object> status = new HashMap<>();
            // Keep the existing fields to maintain compatibility
            status.put("currentSize", videoQueue.size());
            status.put("maxSize", maxQueueSize);
            status.put("remainingCapacity", videoQueue.remainingCapacity());

            // Add a new field for the simplified status
            boolean isFull = videoQueue.remainingCapacity() == 0;
            status.put("isFull", isFull);

            messagingTemplate.convertAndSend("/topic/queue-status", status);
        }
    }
}