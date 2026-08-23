package com.zuehlke.securesoftwaredevelopment.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class CarImageStorageService {
    private static final Path UPLOAD_DIRECTORY = Paths.get("uploads");

    public String store(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }

        String fileName = image.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Uploaded image must have a file name");
        }

        Path destination = UPLOAD_DIRECTORY.resolve(fileName);
        try {
            Files.createDirectories(UPLOAD_DIRECTORY);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.copy(image.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException exception) {
            Path workingDirectory = Paths.get("").toAbsolutePath();
            throw new IllegalStateException(
                    "Could not store image at " + destination.toAbsolutePath()
                            + ". Working directory: " + workingDirectory
                            + ". Java classpath: " + System.getProperty("java.class.path"),
                    exception);
        }
    }

    public Resource loadForDisplay(String fileName) {
        Path uploadRoot = UPLOAD_DIRECTORY.toAbsolutePath().normalize();
        Path file = uploadRoot.resolve(fileName).normalize();
        if (!file.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid image path");
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Image not found");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Image not found", e);
        }
    }
}
