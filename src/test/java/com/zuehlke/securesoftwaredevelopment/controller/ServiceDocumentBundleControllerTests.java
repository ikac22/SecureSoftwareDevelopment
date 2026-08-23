package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.service.ServiceDocumentBundleService;
import com.zuehlke.securesoftwaredevelopment.service.ServiceDocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDocumentBundleControllerTests {
    @TempDir
    Path tempDirectory;

    private ServiceDocumentBundleService bundleService;
    private ServiceDocumentBundleController controller;

    @BeforeEach
    void setUp() {
        bundleService = mock(ServiceDocumentBundleService.class);
        controller = new ServiceDocumentBundleController(bundleService);
    }

    @Test
    void downloadDelegatesCustomerSelectionAndReturnsTarAttachment() throws Exception {
        User customer = new User(42, "customer", "password");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(customer);
        byte[] archive = new byte[]{1, 2, 3, 4};
        when(bundleService.createBundle(127, 42, Arrays.asList(
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.WORK_DETAILED)))
                .thenReturn(archive);

        ResponseEntity<byte[]> response = controller.downloadBundle(
                127,
                Arrays.asList(
                        ServiceDocumentBundleService.SERVICE_OVERVIEW,
                        ServiceDocumentBundleService.WORK_DETAILED),
                authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/x-tar");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("service-127-documents.tar");
        assertThat(response.getBody()).isEqualTo(archive);
        verify(bundleService).createBundle(127, 42, Arrays.asList(
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.WORK_DETAILED));
    }

    @Test
    void downloadRunsControllerServiceTarFlowAndReturnsSelectedDocuments() throws Exception {
        Service completedService = completedService(127, 42);
        ServiceRepository repository = mock(ServiceRepository.class);
        when(repository.findById(127)).thenReturn(Optional.of(completedService));

        ServiceDocumentStorage storage = new ServiceDocumentStorage(tempDirectory.toString());
        Path serviceDirectory = storage.serviceDirectory(127);
        Files.createDirectories(serviceDirectory);
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW),
                "overview".getBytes(StandardCharsets.UTF_8));
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.PARTS_DETAILED),
                "parts".getBytes(StandardCharsets.UTF_8));
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.WORK_DETAILED),
                "work".getBytes(StandardCharsets.UTF_8));

        ServiceDocumentBundleController realController = new ServiceDocumentBundleController(
                new ServiceDocumentBundleService(repository, storage, false));
        User customer = new User(42, "customer", "password");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(customer);

        ResponseEntity<byte[]> response = realController.downloadBundle(
                127,
                Arrays.asList(
                        ServiceDocumentBundleService.SERVICE_OVERVIEW,
                        ServiceDocumentBundleService.WORK_DETAILED),
                authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/x-tar");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("service-127-documents.tar");

        Path returnedArchive = tempDirectory.resolve("controller-e2e.tar");
        Files.write(returnedArchive, Objects.requireNonNull(response.getBody()));
        assertThat(listArchiveMembers(returnedArchive)).containsExactly(
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.WORK_DETAILED);
    }

    @Test
    void downloadRequiresSqlCustomerPrincipal() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("ldap-technician");

        assertThatThrownBy(() -> controller.downloadBundle(
                127,
                Collections.singletonList(ServiceDocumentBundleService.SERVICE_OVERVIEW),
                authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
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

    private List<String> listArchiveMembers(Path archive) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("tar", "-tf", archive.toString())
                .redirectErrorStream(true)
                .start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
        }
        int exitCode = process.waitFor();
        assertThat(exitCode).isZero();
        return lines;
    }
}
