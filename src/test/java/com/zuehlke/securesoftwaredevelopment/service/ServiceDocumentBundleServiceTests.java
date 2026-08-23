package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceDocumentBundleServiceTests {
    @TempDir
    Path tempDirectory;

    @Test
    void extractsOnlySelectedDocumentsThenReturnsTarBundle() throws Exception {
        ServiceRepository repository = repositoryWith(completedService(127, 42));
        ServiceDocumentStorage storage = storageWithArchive(127);
        ServiceDocumentBundleService bundleService =
                new ServiceDocumentBundleService(repository, storage);

        byte[] bundle = bundleService.createBundle(127, 42, Arrays.asList(
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.WORK_DETAILED));

        Path archive = tempDirectory.resolve("selected.tar");
        Files.write(archive, bundle);
        assertThat(listArchiveMembers(archive, false)).containsExactly(
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.WORK_DETAILED);
        assertThat(storage.serviceArchive(127)).exists();
        assertThat(storage.serviceDirectory(127).resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW))
                .doesNotExist();
        assertThat(storage.serviceDirectory(127).resolve(ServiceDocumentBundleService.WORK_DETAILED))
                .doesNotExist();
    }

    @Test
    void rejectsCrossCustomerDownloadEvenWhenServiceIsCompleted() throws Exception {
        ServiceRepository repository = repositoryWith(completedService(127, 99));
        ServiceDocumentStorage storage = storageWithArchive(127);
        ServiceDocumentBundleService bundleService =
                new ServiceDocumentBundleService(repository, storage);

        assertThatThrownBy(() -> bundleService.createBundle(127, 42,
                Collections.singletonList(ServiceDocumentBundleService.SERVICE_OVERVIEW)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void extractionArgumentsReachGnuTarToCommandParser() throws Exception {
        ServiceRepository repository = repositoryWith(completedService(127, 42));
        ServiceDocumentStorage storage = storageWithArchive(127);
        ServiceDocumentBundleService bundleService =
                new ServiceDocumentBundleService(repository, storage);

        String injectedExtractionOption =
                "--to-command=cat>service-overview.pdf;cd${IFS}..;touch${IFS}bundle-proof.pdf";
        byte[] bundle = bundleService.createBundle(127, 42, Arrays.asList(
                injectedExtractionOption,
                ServiceDocumentBundleService.SERVICE_OVERVIEW));

        Path returnedArchive = tempDirectory.resolve("injected-selected.tar");
        Files.write(returnedArchive, bundle);
        assertThat(listArchiveMembers(returnedArchive, false))
                .containsExactly(ServiceDocumentBundleService.SERVICE_OVERVIEW);
        assertThat(storage.serviceDirectory(127).resolve("bundle-proof.pdf")).exists();
        assertThat(storage.serviceArchive(127)).exists();

        Files.deleteIfExists(storage.serviceDirectory(127).resolve("bundle-proof.pdf"));
    }

    private ServiceDocumentStorage storageWithArchive(int serviceId) throws Exception {
        ServiceDocumentStorage storage = new ServiceDocumentStorage(tempDirectory.toString());
        Path serviceDirectory = storage.serviceDirectory(serviceId);
        Files.createDirectories(serviceDirectory);
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW),
                "overview".getBytes(StandardCharsets.UTF_8));
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.PARTS_DETAILED),
                "parts".getBytes(StandardCharsets.UTF_8));
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.WORK_DETAILED),
                "work".getBytes(StandardCharsets.UTF_8));

        Process process = new ProcessBuilder(
                "tar", "-czf", storage.serviceArchive(serviceId).toString(), "--",
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.PARTS_DETAILED,
                ServiceDocumentBundleService.WORK_DETAILED)
                .directory(serviceDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        assertThat(process.waitFor()).isZero();

        Files.delete(serviceDirectory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW));
        Files.delete(serviceDirectory.resolve(ServiceDocumentBundleService.PARTS_DETAILED));
        Files.delete(serviceDirectory.resolve(ServiceDocumentBundleService.WORK_DETAILED));
        return storage;
    }

    private ServiceRepository repositoryWith(Service service) {
        ServiceRepository repository = mock(ServiceRepository.class);
        when(repository.findById(service.getId())).thenReturn(Optional.of(service));
        return repository;
    }

    private Service completedService(int serviceId, int personId) {
        return new Service(
                serviceId,
                personId,
                null,
                null,
                "Demo car",
                "Completed demo service",
                ServiceStatus.COMPLETED,
                "marko.markovic",
                60,
                LocalDateTime.of(2026, 8, 23, 12, 0),
                null
        );
    }

    private List<String> listArchiveMembers(Path archive, boolean gzip)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder("tar", gzip ? "-tzf" : "-tf", archive.toString())
                .redirectErrorStream(true)
                .start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = normalizeArchiveMember(line.trim());
                if (!normalized.isEmpty()) {
                    lines.add(normalized);
                }
            }
        }
        int exitCode = process.waitFor();
        assertThat(exitCode).isZero();
        return lines;
    }

    private String normalizeArchiveMember(String member) {
        if (".".equals(member) || "./".equals(member)) {
            return "";
        }
        return member.startsWith("./") ? member.substring(2) : member;
    }
}
