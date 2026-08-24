package com.zuehlke.securesoftwaredevelopment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalGalleryServiceTests {

    @TempDir
    Path tempDirectory;

    @Test
    void storesAndListsRegularGalleryImages() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "local-photo.jpg",
                "image/jpeg",
                "photo-data".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService.UploadResult result = service.store(7, image, "road-trip.jpg", false);

        assertThat(result.getStatus()).isEqualTo(PersonalGalleryService.UploadStatus.CREATED);
        assertThat(service.listImages(7)).containsExactly("road-trip.jpg");
        assertThat(new String(Files.readAllBytes(galleryRoot.resolve("7/road-trip.jpg")), StandardCharsets.UTF_8))
                .isEqualTo("photo-data");
    }

    @Test
    void rejectsTraversalForANewFileBecauseSanitizationIsReached() {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "photo.jpg",
                "image/jpeg",
                "photo-data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.store(1, image, "../../new-file.spel", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".jpg or .png");
    }

    @Test
    void existingTraversalTargetRequestsOverwriteBeforeSanitization() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot.resolve("1"));
        Path outsideTarget = tempDirectory.resolve("pricing.spel");
        Files.write(outsideTarget, "trusted-policy".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());
        MockMultipartFile replacement = new MockMultipartFile(
                "image",
                "photo.jpg",
                "image/jpeg",
                "replacement-policy".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService.UploadResult firstAttempt = service.store(
                1, replacement, "../../pricing.spel", false);

        assertThat(firstAttempt.getStatus())
                .isEqualTo(PersonalGalleryService.UploadStatus.REQUIRES_OVERWRITE);
        assertThat(new String(Files.readAllBytes(outsideTarget), StandardCharsets.UTF_8))
                .isEqualTo("trusted-policy");
    }

    @Test
    void overwriteBranchWritesExistingUnsanitizedTraversalTarget() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot.resolve("1"));
        Path outsideTarget = tempDirectory.resolve("pricing.spel");
        Files.write(outsideTarget, "trusted-policy".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());
        MockMultipartFile replacement = new MockMultipartFile(
                "image",
                "photo.jpg",
                "image/jpeg",
                "replacement-policy".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService.UploadResult result = service.store(
                1, replacement, "../../pricing.spel", true);

        assertThat(result.getStatus()).isEqualTo(PersonalGalleryService.UploadStatus.OVERWRITTEN);
        assertThat(new String(Files.readAllBytes(outsideTarget), StandardCharsets.UTF_8))
                .isEqualTo("replacement-policy");
    }

    @Test
    void displayLookupCanResolveOutsideGalleryRoot() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot);
        Path outsideFile = tempDirectory.resolve("README.md");
        Files.write(outsideFile, "outside-gallery".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());

        Resource resource = service.loadForDisplay("../README.md");

        assertThat(resource.exists()).isTrue();
        assertThat(new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8))
                .isEqualTo("outside-gallery");
    }
}
