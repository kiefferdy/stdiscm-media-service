# Media Upload Service

A producer-consumer based media upload service that demonstrates concurrent programming, file I/O, queueing, and network communication.

## Overview

This project consists of two main components:

1. **Producer**: Reads video files from directories and uploads them to the consumer service
2. **Consumer**: Accepts simultaneous media uploads, saves videos, and provides a web interface for viewing

## Features

- Multiple producer threads, each reading from a separate directory
- Multiple consumer threads for processing uploads
- Queue management with configurable maximum size
- Browser-based GUI for viewing uploaded videos
- 10-second video preview on mouse hover
- Full video playback on click
- Queue monitoring and status updates
- Duplicate detection via file hash
- Back pressure notification when queue is full

## Requirements

- Java 17 or higher
- Maven

## Building the Application

```bash
mvn clean package
```

## Running the Consumer

```bash
cd consumer
java -jar target/consumer-1.0-SNAPSHOT.jar <consumer-threads> <max-queue-size> <port>
```

Example:

```bash
java -jar target/consumer-1.0-SNAPSHOT.jar 4 10 9000
```

This starts the consumer with:
- 4 consumer threads
- Maximum queue size of 10
- Listening on port 9000

The web interface will be available at `http://localhost:8080`

## Running the Producer

```bash
cd producer
java -jar target/producer-1.0-SNAPSHOT-jar-with-dependencies.jar -p <producers> -h <host> -t <port> -d <directories>
```

Example:

```bash
java -jar target/producer-1.0-SNAPSHOT-jar-with-dependencies.jar -p 2 -h localhost -t 9000 -d /path/to/videos1,/path/to/videos2
```

This starts:
- 2 producer threads
- Connecting to localhost:9000
- Reading from the specified directories

## Implementation Details

### Producer

- Monitors directories for video files
- Calculates MD5 hash for duplicate detection
- Sends files to consumer via socket connection
- Handles back pressure when queue is full

### Consumer

- Spring Boot application with Thymeleaf templates
- Uses a BlockingQueue for the upload queue
- Multiple threads for processing video uploads
- WebSocket for real-time UI updates
- Video thumbnail and preview generation
- Web interface for video playback

## Bonus Features

1. **Queue Full Notification**: The consumer informs producers when the queue is full
2. **Duplicate Detection**: Uses MD5 hashing to prevent duplicate uploads
3. **Preview Generation**: Creates frame sequences for video preview
