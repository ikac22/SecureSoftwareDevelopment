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
    void regularExistingImageCanStillBeOverwritten() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());

        MockMultipartFile original = new MockMultipartFile(
                "image", "original.jpg", "image/jpeg", "before".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile replacement = new MockMultipartFile(
                "image", "replacement.jpg", "image/jpeg", "after".getBytes(StandardCharsets.UTF_8));

        service.store(1, original, "photo.jpg", false);
        PersonalGalleryService.UploadResult probe = service.store(1, replacement, "photo.jpg", false);
        PersonalGalleryService.UploadResult overwritten = service.store(1, replacement, "photo.jpg", true);

        assertThat(probe.getStatus()).isEqualTo(PersonalGalleryService.UploadStatus.REQUIRES_OVERWRITE);
        assertThat(overwritten.getStatus()).isEqualTo(PersonalGalleryService.UploadStatus.OVERWRITTEN);
        assertThat(new String(Files.readAllBytes(galleryRoot.resolve("1/photo.jpg")), StandardCharsets.UTF_8))
                .isEqualTo("after");
    }

    @Test
    void rejectsTraversalForANewFile() {
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
    void existingTraversalTargetIsRejectedBeforeOverwriteProbe() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot.resolve("1"));
        Path outsideTarget = tempDirectory.resolve("outside.jpg");
        Files.write(outsideTarget, "before".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());
        MockMultipartFile replacement = new MockMultipartFile(
                "image",
                "photo.jpg",
                "image/jpeg",
                "after".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.store(1, replacement, "../../outside.jpg", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported characters");
        assertThat(new String(Files.readAllBytes(outsideTarget), StandardCharsets.UTF_8))
                .isEqualTo("before");
    }

    @Test
    void overwriteTraversalCannotModifyExistingOutsideTarget() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot.resolve("1"));
        Path outsideTarget = tempDirectory.resolve("outside.jpg");
        Files.write(outsideTarget, "before".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());
        MockMultipartFile replacement = new MockMultipartFile(
                "image",
                "photo.jpg",
                "image/jpeg",
                "after".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.store(1, replacement, "../../outside.jpg", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported characters");
        assertThat(new String(Files.readAllBytes(outsideTarget), StandardCharsets.UTF_8))
                .isEqualTo("before");
    }

    @Test
    void displayLookupRejectsResourceOutsideGalleryRoot() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot);
        Path outsideFile = tempDirectory.resolve("README.md");
        Files.write(outsideFile, "outside-gallery".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());

        assertThatThrownBy(() -> service.loadForDisplay("../README.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes the gallery root");
    }

    @Test
    void displayLookupStillReturnsResourceInsideGalleryRoot() throws Exception {
        Path galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot.resolve("1"));
        Path image = galleryRoot.resolve("1/photo.jpg");
        Files.write(image, "inside-gallery".getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());
        Resource resource = service.loadForDisplay("1/photo.jpg");

        assertThat(resource.exists()).isTrue();
        assertThat(new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8))
                .isEqualTo("inside-gallery");
    }
}
