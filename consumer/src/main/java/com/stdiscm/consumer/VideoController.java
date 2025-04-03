package com.stdiscm.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;

@Controller
public class VideoController {

    @Autowired
    private VideoDatabaseService videoDatabaseService;
    
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("videos", videoDatabaseService.getAllVideos());
        return "index";
    }
    
    @GetMapping("/video/{id}")
    public ResponseEntity<Resource> serveVideo(@PathVariable String id) {
        VideoMetadata metadata = videoDatabaseService.getVideo(id);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        
        File file = new File(metadata.getFilePath());
        Resource resource = new FileSystemResource(file);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + metadata.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }
    
    @GetMapping("/thumbnail/{id}")
    public ResponseEntity<Resource> serveThumbnail(@PathVariable String id) {
        VideoMetadata metadata = videoDatabaseService.getVideo(id);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        
        File file = new File(metadata.getThumbnailPath());
        Resource resource = new FileSystemResource(file);
        
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }
    
    @GetMapping("/preview/{id}/{frame}")
    public ResponseEntity<Resource> servePreviewFrame(@PathVariable String id, @PathVariable String frame) {
        VideoMetadata metadata = videoDatabaseService.getVideo(id);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        
        File file = new File(metadata.getPreviewPath(), frame);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        
        Resource resource = new FileSystemResource(file);
        
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }
}
