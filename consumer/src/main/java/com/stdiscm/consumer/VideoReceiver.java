package com.stdiscm.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoReceiver implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(VideoReceiver.class);
    
    private final int port;
    private final BlockingQueue<VideoFile> videoQueue;
    private final Set<String> processedHashes;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private ServerSocket serverSocket;
    private ExecutorService connectionHandlers;
    
    public VideoReceiver(int port, BlockingQueue<VideoFile> videoQueue, Set<String> processedHashes) {
        this.port = port;
        this.videoQueue = videoQueue;
        this.processedHashes = processedHashes;
        this.connectionHandlers = Executors.newCachedThreadPool();
    }
    
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(1000); // 1 second timeout to check running flag
            logger.info("VideoReceiver started on port {}", port);
            
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    connectionHandlers.submit(() -> handleClientConnection(clientSocket));
                } catch (SocketTimeoutException e) {
                    // This is expected due to the socket timeout, continue the loop
                    continue;
                } catch (IOException e) {
                    if (running.get()) {
                        logger.error("Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error creating server socket", e);
        } finally {
            closeServerSocket();
            shutdownConnectionHandlers();
        }
    }
    
    private void handleClientConnection(Socket clientSocket) {
        logger.info("Handling connection from {}", clientSocket.getRemoteSocketAddress());
        
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
            
            // Read file metadata
            String fileName = dis.readUTF();
            String fileHash = dis.readUTF();
            long fileSize = dis.readLong();
            
            logger.info("Received request to upload: {} (size: {} bytes, hash: {})", 
                    fileName, fileSize, fileHash);
            
            // Check if file already exists (by hash)
            boolean isDuplicate = false;
            synchronized (processedHashes) {
                isDuplicate = processedHashes.contains(fileHash);
            }
            
            if (isDuplicate) {
                logger.info("Rejecting duplicate file: {}", fileName);
                dos.writeBoolean(false); // Reject the upload
                dos.flush();
                return;
            }
            
            // Check if queue has space
            boolean queueHasSpace = videoQueue.remainingCapacity() > 0;
            dos.writeBoolean(queueHasSpace);
            dos.flush();
            
            if (!queueHasSpace) {
                logger.warn("Rejecting upload due to full queue: {}", fileName);
                return;
            }
            
            // Read the file data
            byte[] fileData = new byte[(int) fileSize];
            dis.readFully(fileData);
            
            // Create VideoFile object and add to queue
            VideoFile videoFile = new VideoFile(fileName, fileHash, fileData);
            boolean added = videoQueue.offer(videoFile);
            
            // Send result
            dos.writeBoolean(added);
            dos.flush();
            
            if (added) {
                synchronized (processedHashes) {
                    processedHashes.add(fileHash);
                }
                logger.info("Successfully queued file: {}", fileName);
            } else {
                logger.warn("Failed to add file to queue: {}", fileName);
            }
            
        } catch (IOException e) {
            logger.error("Error handling client connection", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }
    
    public void stop() {
        running.set(false);
        closeServerSocket();
        shutdownConnectionHandlers();
    }
    
    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("Error closing server socket", e);
            }
        }
    }
    
    private void shutdownConnectionHandlers() {
        if (connectionHandlers != null) {
            connectionHandlers.shutdown();
            try {
                if (!connectionHandlers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    connectionHandlers.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionHandlers.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
