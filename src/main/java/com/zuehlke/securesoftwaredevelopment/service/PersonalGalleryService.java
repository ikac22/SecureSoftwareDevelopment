package com.zuehlke.securesoftwaredevelopment.service;

import org.springframework.beans.factory.annotation.Value;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PersonalGalleryService {
    private final Path galleryRoot;

    public PersonalGalleryService(@Value("${app.gallery.root:user-galleries}") String galleryRoot) {
        this.galleryRoot = Paths.get(galleryRoot);
    }

    public List<String> listImages(int personId) {
        Path userGallery = galleryRoot.resolve(Integer.toString(personId));
        if (!Files.isDirectory(userGallery)) {
            return Collections.emptyList();
        }

        try (Stream<Path> files = Files.list(userGallery)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(this::hasImageExtension)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read personal gallery", exception);
        }
    }

    public Resource loadForDisplay(String requestedPath) {
        if (requestedPath == null || requestedPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Image path is required");
        }

        Path supplied = Paths.get(requestedPath);
        if (supplied.isAbsolute()) {
            throw new IllegalArgumentException("Absolute image paths are not supported");
        }

        // Deliberately vulnerable for the educational scenario: the path is normalized,
        // but there is no post-resolution containment check against galleryRoot.
        Path file = galleryRoot.resolve(supplied).normalize();
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable() || Files.isDirectory(file)) {
                throw new IllegalArgumentException("Image not found");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Image not found", exception);
        }
    }

    public UploadResult store(int personId,
                              MultipartFile image,
                              String requestedFileName,
                              boolean overwrite) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Please select an image to upload");
        }
        if (requestedFileName == null || requestedFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Server file name is required");
        }

        Path userGallery = galleryRoot.resolve(Integer.toString(personId));
        Path destination = userGallery.resolve(requestedFileName);

        try {
            Files.createDirectories(userGallery);

            // Intentional validation-order flaw for the teaching example.
            // Existing targets enter this branch before the file name is sanitized.
            if (Files.exists(destination)) {
                if (!overwrite) {
                    return UploadResult.requiresOverwrite(requestedFileName);
                }

                Files.copy(image.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                return UploadResult.overwritten(requestedFileName);
            }

            validateNewFileName(requestedFileName);
            Files.copy(image.getInputStream(), destination);
            return UploadResult.created(requestedFileName);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store gallery image", exception);
        }
    }

    private void validateNewFileName(String requestedFileName) {
        if (!hasImageExtension(requestedFileName)) {
            throw new IllegalArgumentException("Gallery images must use a .jpg or .png extension");
        }
        if (requestedFileName.contains("..")
                || requestedFileName.contains("/")
                || requestedFileName.contains("\\")
                || !requestedFileName.matches("[A-Za-z0-9._ -]+")) {
            throw new IllegalArgumentException("Gallery image name contains unsupported characters");
        }
    }

    private boolean hasImageExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".png");
    }

    public enum UploadStatus {
        CREATED,
        REQUIRES_OVERWRITE,
        OVERWRITTEN
    }

    public static class UploadResult {
        private final UploadStatus status;
        private final String fileName;

        private UploadResult(UploadStatus status, String fileName) {
            this.status = status;
            this.fileName = fileName;
        }

        public static UploadResult created(String fileName) {
            return new UploadResult(UploadStatus.CREATED, fileName);
        }

        public static UploadResult requiresOverwrite(String fileName) {
            return new UploadResult(UploadStatus.REQUIRES_OVERWRITE, fileName);
        }

        public static UploadResult overwritten(String fileName) {
            return new UploadResult(UploadStatus.OVERWRITTEN, fileName);
        }

        public UploadStatus getStatus() {
            return status;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
