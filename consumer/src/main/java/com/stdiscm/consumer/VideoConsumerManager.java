package com.stdiscm.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class VideoConsumerManager {
    private static final Logger logger = LoggerFactory.getLogger(VideoConsumerManager.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private VideoDatabaseService videoDatabaseService;
    
    @Autowired
    private QueueMonitor queueMonitor;
    
    private BlockingQueue<VideoFile> videoQueue;
    private ExecutorService executorService;
    private Set<String> processedHashes = new HashSet<>();
    private VideoReceiver videoReceiver;
    private final String uploadsDir = "uploads";
    private final String thumbnailsDir = "uploads/thumbnails";
    
    public void initialize(int consumerThreads, int maxQueueSize, int port) {
        logger.info("Initializing VideoConsumerManager with {} threads, queue size: {}, port: {}", 
                consumerThreads, maxQueueSize, port);
        
        // Create uploads directory if it doesn't exist
        createDirectories();
        
        // Initialize queue and thread pool
        this.videoQueue = new LinkedBlockingQueue<>(maxQueueSize);
        this.executorService = Executors.newFixedThreadPool(consumerThreads);
        
        // Start consumer threads
        for (int i = 0; i < consumerThreads; i++) {
            VideoConsumer consumer = new VideoConsumer(i + 1, videoQueue, uploadsDir, thumbnailsDir, 
                    messagingTemplate, videoDatabaseService);
            executorService.submit(consumer);
        }
        
        // Start the socket server to receive videos
        videoReceiver = new VideoReceiver(port, videoQueue, processedHashes);
        new Thread(videoReceiver).start();
        
        // Initialize queue monitor
        queueMonitor.initialize(videoQueue, maxQueueSize);
        
        logger.info("VideoConsumerManager initialized successfully");
    }
    
    private void createDirectories() {
        try {
            Path uploadsPath = Paths.get(uploadsDir);
            if (!Files.exists(uploadsPath)) {
                Files.createDirectories(uploadsPath);
            }
            
            Path thumbnailsPath = Paths.get(thumbnailsDir);
            if (!Files.exists(thumbnailsPath)) {
                Files.createDirectories(thumbnailsPath);
            }
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
            throw new RuntimeException("Failed to create required directories", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down VideoConsumerManager");
        
        if (videoReceiver != null) {
            videoReceiver.stop();
        }
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        
        logger.info("VideoConsumerManager shutdown complete");
    }
}
