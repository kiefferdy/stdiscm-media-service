package com.stdiscm.producer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoProducer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(VideoProducer.class);
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>();
    
    static {
        VIDEO_EXTENSIONS.add("mp4");
        VIDEO_EXTENSIONS.add("avi");
        VIDEO_EXTENSIONS.add("mov");
        VIDEO_EXTENSIONS.add("mkv");
        VIDEO_EXTENSIONS.add("wmv");
        VIDEO_EXTENSIONS.add("flv");
        VIDEO_EXTENSIONS.add("webm");
    }
    
    private final int id;
    private final String directoryPath;
    private final String consumerHost;
    private final int consumerPort;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    public VideoProducer(int id, String directoryPath, String consumerHost, int consumerPort) {
        this.id = id;
        this.directoryPath = directoryPath;
        this.consumerHost = consumerHost;
        this.consumerPort = consumerPort;
    }
    
    @Override
    public void run() {
        logger.info("Producer {} started - monitoring directory: {}", id, directoryPath);
        
        Path directory = Paths.get(directoryPath);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            logger.error("Producer {} - Directory does not exist: {}", id, directoryPath);
            return;
        }
        
        Set<String> processedFiles = new HashSet<>();
        
        while (running.get()) {
            try {
                File[] files = new File(directoryPath).listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && isVideoFile(file) && !processedFiles.contains(file.getName())) {
                            // Process the file
                            if (uploadVideo(file)) {
                                processedFiles.add(file.getName());
                                logger.info("Producer {} - Successfully uploaded: {}", id, file.getName());
                            }
                        }
                    }
                }
                
                // Sleep before checking for new files
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                logger.warn("Producer {} was interrupted", id);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Producer {} encountered an error", id, e);
            }
        }
        
        logger.info("Producer {} stopped", id);
    }
    
    private boolean isVideoFile(File file) {
        String extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
        return VIDEO_EXTENSIONS.contains(extension);
    }
    
    private boolean uploadVideo(File videoFile) {
        try (Socket socket = new Socket(consumerHost, consumerPort)) {
            // Set socket timeout
            socket.setSoTimeout(30000);  // 30 seconds timeout
            
            // Get file data
            byte[] fileData = FileUtils.readFileToByteArray(videoFile);
            String fileName = videoFile.getName();
            String fileHash = calculateMD5(videoFile);
            
            // Prepare output stream
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            
            // Send file metadata
            dos.writeUTF(fileName);
            dos.writeUTF(fileHash);
            dos.writeLong(fileData.length);
            
            // Check if consumer accepts the file (queue might be full)
            boolean accepted = dis.readBoolean();
            if (!accepted) {
                logger.warn("Producer {} - Upload rejected (queue full): {}", id, fileName);
                return false;
            }
            
            // Send the file data
            dos.write(fileData);
            dos.flush();
            
            // Check upload success
            boolean success = dis.readBoolean();
            
            return success;
        } catch (IOException e) {
            logger.error("Producer {} - Error uploading file: {}", id, videoFile.getName(), e);
            return false;
        }
    }
    
    private String calculateMD5(File file) {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = bis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Error calculating MD5 for file: {}", file.getName(), e);
            return "";
        }
    }
    
    public void stop() {
        running.set(false);
    }
}
