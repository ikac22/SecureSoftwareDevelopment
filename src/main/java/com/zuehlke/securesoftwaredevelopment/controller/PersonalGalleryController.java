package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.service.PersonalGalleryService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PersonalGalleryController {
    private final PersonalGalleryService galleryService;

    public PersonalGalleryController(PersonalGalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @GetMapping("/gallery/image")
    public ResponseEntity<Resource> image(@RequestParam("path") String path) {
        Resource resource = galleryService.loadForDisplay(path);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String detected = Files.probeContentType(resource.getFile().toPath());
            if (detected != null) {
                mediaType = MediaType.parseMediaType(detected);
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Keep a generic binary response when the platform cannot determine a type.
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }

    @PostMapping(value = "/my-gallery/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("image") MultipartFile image,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "overwrite", required = false, defaultValue = "false") boolean overwrite,
            Authentication authentication) {
        User customer = requireCustomer(authentication);

        PersonalGalleryService.UploadResult result = galleryService.store(
                customer.getId(), image, fileName, overwrite);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileName", result.getFileName());
        response.put("status", result.getStatus().name());

        if (result.getStatus() == PersonalGalleryService.UploadStatus.REQUIRES_OVERWRITE) {
            response.put("requiresOverwrite", true);
            response.put("message", "A file with this server name already exists. Confirm overwrite to continue.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        response.put("requiresOverwrite", false);
        return ResponseEntity.ok(response);
    }

    private User requireCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Personal galleries are available to customer accounts only");
        }
        return (User) authentication.getPrincipal();
    }
}
