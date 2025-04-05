package com.stdiscm.consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoConsumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(VideoConsumer.class);

    private final int id;
    private final BlockingQueue<VideoFile> videoQueue;
    private final String uploadDirectory;
    private final String thumbnailDirectory;
    private final SimpMessagingTemplate messagingTemplate;
    private final VideoDatabaseService videoDatabaseService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Compression settings
    private final boolean compressVideo = true;
    private final int compressionBitrate = 1000000; // 1 Mbps
    private final String compressionFormat = "mp4";

    public VideoConsumer(int id, BlockingQueue<VideoFile> videoQueue, String uploadDirectory,
            String thumbnailDirectory, SimpMessagingTemplate messagingTemplate,
            VideoDatabaseService videoDatabaseService) {
        this.id = id;
        this.videoQueue = videoQueue;
        this.uploadDirectory = uploadDirectory;
        this.thumbnailDirectory = thumbnailDirectory;
        this.messagingTemplate = messagingTemplate;
        this.videoDatabaseService = videoDatabaseService;
    }

    @Override
    public void run() {
        logger.info("VideoConsumer {} started", id);

        while (running.get()) {
            try {
                // Try to get a video file from the queue, with timeout
                VideoFile videoFile = videoQueue.poll(1, TimeUnit.SECONDS);

                if (videoFile != null) {
                    processVideo(videoFile);
                }
            } catch (InterruptedException e) {
                logger.warn("VideoConsumer {} was interrupted", id);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("VideoConsumer {} encountered an error", id, e);
            }
        }

        logger.info("VideoConsumer {} stopped", id);
    }

    private void processVideo(VideoFile videoFile) {
        String fileName = videoFile.getFileName();
        String fileHash = videoFile.getFileHash();
        byte[] fileData = videoFile.getFileData();

        logger.info("Consumer {} processing video: {}", id, fileName);

        try {
            // Save the original video file temporarily
            String tempPath = saveVideoFile(fileName + ".temp", fileData);
            File tempFile = new File(tempPath);

            // Compress video if enabled
            String savedPath;
            long finalFileSize;

            if (compressVideo) {
                // Create a unique filename for the compressed video
                String compressedFileName = UUID.randomUUID().toString() + "-" +
                        FilenameUtils.getBaseName(fileName) + "." + compressionFormat;
                String compressedPath = Paths.get(uploadDirectory, compressedFileName).toString();

                // Compress the video
                logger.info("Consumer {} compressing video: {} (original size: {} bytes)",
                        id, fileName, fileData.length);

                compressVideo(tempPath, compressedPath);

                // Get the size of the compressed file
                File compressedFile = new File(compressedPath);
                finalFileSize = compressedFile.length();

                logger.info("Consumer {} compressed video: {} (new size: {} bytes, reduction: {}%)",
                        id, fileName, finalFileSize,
                        Math.round((1 - ((double) finalFileSize / fileData.length)) * 100));

                // Delete the temporary file
                tempFile.delete();

                savedPath = compressedPath;
            } else {
                // If compression is disabled, just rename the temp file
                String finalFileName = UUID.randomUUID().toString() + "-" + fileName;
                String finalPath = Paths.get(uploadDirectory, finalFileName).toString();

                File finalFile = new File(finalPath);
                tempFile.renameTo(finalFile);

                savedPath = finalPath;
                finalFileSize = fileData.length;
            }

            // Generate thumbnail
            String thumbnailPath = generateThumbnail(savedPath, fileHash);

            // Generate 10-second preview (extract frames for preview)
            String previewPath = generatePreview(savedPath, fileHash);

            // Add to database
            VideoMetadata metadata = new VideoMetadata(
                    fileHash,
                    fileName,
                    savedPath,
                    thumbnailPath,
                    previewPath,
                    finalFileSize, // Use the compressed file size
                    videoFile.getUploadTime());

            videoDatabaseService.addVideo(metadata);

            // Notify clients via WebSocket
            messagingTemplate.convertAndSend("/topic/videos", metadata);

            logger.info("Consumer {} successfully processed video: {}", id, fileName);

        } catch (Exception e) {
            logger.error("Consumer {} failed to process video: {}", id, fileName, e);
        }
    }

    private void compressVideo(String inputPath, String outputPath) throws IOException {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input video file not found: " + inputPath);
        }

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
            grabber.start();

            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                    outputPath,
                    grabber.getImageWidth(),
                    grabber.getImageHeight(),
                    grabber.getAudioChannels());

            // Setup the recorder with compression parameters
            recorder.setFormat(compressionFormat);
            recorder.setVideoBitrate(compressionBitrate);
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            recorder.setAudioCodec(grabber.getAudioCodec());
            recorder.setFrameRate(grabber.getFrameRate());
            recorder.setSampleRate(grabber.getSampleRate());

            // Start the recorder
            recorder.start();

            // Transfer frames from grabber to recorder
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                recorder.record(frame);
            }

            // Close the recorder
            recorder.stop();
            recorder.release();

            grabber.stop();
        } catch (Exception e) {
            throw new IOException("Failed to compress video: " + inputPath, e);
        }
    }

    private String saveVideoFile(String fileName, byte[] fileData) throws IOException {
        Path filePath = Paths.get(uploadDirectory, fileName);

        // Save the file
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(fileData);
        }

        return filePath.toString();
    }

    private String generateThumbnail(String videoPath, String fileHash) throws IOException {
        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            throw new IOException("Video file not found: " + videoPath);
        }

        String thumbnailFileName = fileHash + ".jpg";
        Path thumbnailPath = Paths.get(thumbnailDirectory, thumbnailFileName);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();

            // Seek to 1 second into the video for thumbnail
            grabber.setTimestamp(1000000); // 1 second in microseconds
            Frame frame = grabber.grabImage();

            if (frame != null) {
                try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                    BufferedImage bufferedImage = converter.convert(frame);

                    // Save the thumbnail
                    ImageIO.write(bufferedImage, "jpg", thumbnailPath.toFile());
                }
            } else {
                throw new IOException("Could not grab frame from video: " + videoPath);
            }

            grabber.stop();
        } catch (Exception e) {
            throw new IOException("Failed to generate thumbnail for video: " + videoPath, e);
        }

        return thumbnailPath.toString();
    }

    private String generatePreview(String videoPath, String fileHash) throws IOException {
        // Extract frames for preview (we'll actually create a set of preview images for
        // the frontend)
        // This implementation extracts 10 frames (one per second) for a 10-second
        // preview

        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            throw new IOException("Video file not found: " + videoPath);
        }

        // Create a directory for preview frames
        String previewDirName = fileHash + "-preview";
        Path previewDirPath = Paths.get(thumbnailDirectory, previewDirName);
        Files.createDirectories(previewDirPath);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();

            // Check if video is longer than 10 seconds
            long duration = grabber.getLengthInTime();
            long previewDuration = Math.min(duration, 10 * 1000000); // 10 seconds in microseconds

            // Extract frames at 1-second intervals
            try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                for (int i = 0; i < 10; i++) {
                    long timestamp = i * (previewDuration / 10);
                    grabber.setTimestamp(timestamp);

                    Frame frame = grabber.grabImage();
                    if (frame != null) {
                        BufferedImage bufferedImage = converter.convert(frame);

                        // Save the frame
                        String frameFileName = String.format("frame-%02d.jpg", i);
                        Path framePath = previewDirPath.resolve(frameFileName);
                        ImageIO.write(bufferedImage, "jpg", framePath.toFile());
                    }
                }
            }

            grabber.stop();
        } catch (Exception e) {
            throw new IOException("Failed to generate preview for video: " + videoPath, e);
        }

        return previewDirPath.toString();
    }

    public void stop() {
        running.set(false);
    }
}