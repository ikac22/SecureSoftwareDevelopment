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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class Cwe78ExperimentTests {
    private static final int SERVICE_ID = 127;
    private static final int CUSTOMER_ID = 42;
    private static final String ATTACK_ARGUMENT =
            "--to-command=cat>service-overview.pdf;touch${IFS}bundle-extra.pdf";

    @TempDir
    Path tempDirectory;

    private MockMvc mockMvc;
    private Authentication customerAuthentication;
    private ServiceDocumentStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        ServiceRepository repository = mock(ServiceRepository.class);
        when(repository.findById(SERVICE_ID)).thenReturn(Optional.of(completedService()));

        storage = new ServiceDocumentStorage(tempDirectory.toString());
        createPersistentArchive();

        ServiceDocumentBundleService bundleService = new ServiceDocumentBundleService(repository, storage);
        ServiceDocumentBundleController controller = new ServiceDocumentBundleController(bundleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        User customer = new User(CUSTOMER_ID, "customer", "password");
        customerAuthentication = new UsernamePasswordAuthenticationToken(
                customer, customer.getPassword(), Collections.emptyList());
    }

    @Test
    void runCwe78Experiment() throws Exception {
        String phase = System.getProperty("cwe78.phase", "mitigated");
        assertTrue("vulnerable".equals(phase) || "mitigated".equals(phase),
                "cwe78.phase must be vulnerable or mitigated");

        MvcResult attackResult = mockMvc.perform(post("/services/{serviceId}/documents/bundle", SERVICE_ID)
                .principal(customerAuthentication)
                .param("files", ServiceDocumentBundleService.SERVICE_OVERVIEW, ATTACK_ARGUMENT))
                .andReturn();

        int status = attackResult.getResponse().getStatus();
        byte[] responseBody = attackResult.getResponse().getContentAsByteArray();
        List<String> members = status == 200
                ? listArchiveMembers(writeTemporaryArchive(responseBody))
                : Collections.emptyList();
        boolean markerPresent = members.contains("bundle-extra.pdf");

        writeEvidence(phase, attackResult, responseBody, members, markerPresent);
        printEvidence(phase, attackResult, members, markerPresent);

        if ("vulnerable".equals(phase)) {
            assertEquals(200, status, "vulnerable application must accept the crafted selection");
            assertTrue(markerPresent,
                    "argument injection must create bundle-extra.pdf in the returned archive");
        } else {
            assertEquals(400, status,
                    "mitigated application must reject the unsupported document selection as an invalid request");
            assertFalse(markerPresent, "mitigation must prevent creation of bundle-extra.pdf");
            verifyNormalDownloadStillWorks();
        }
    }

    private void verifyNormalDownloadStillWorks() throws Exception {
        MvcResult normalResult = mockMvc.perform(post("/services/{serviceId}/documents/bundle", SERVICE_ID)
                .principal(customerAuthentication)
                .param("files",
                        ServiceDocumentBundleService.SERVICE_OVERVIEW,
                        ServiceDocumentBundleService.WORK_DETAILED))
                .andReturn();

        assertEquals(200, normalResult.getResponse().getStatus());
        assertEquals("application/x-tar", normalResult.getResponse().getContentType());

        List<String> members = listArchiveMembers(
                writeTemporaryArchive(normalResult.getResponse().getContentAsByteArray()));
        assertEquals(2, members.size());
        assertTrue(members.contains(ServiceDocumentBundleService.SERVICE_OVERVIEW));
        assertTrue(members.contains(ServiceDocumentBundleService.WORK_DETAILED));
        assertFalse(members.contains("bundle-extra.pdf"));
    }

    private void writeEvidence(String phase,
                               MvcResult result,
                               byte[] responseBody,
                               List<String> members,
                               boolean markerPresent) throws Exception {
        Path evidenceDir = Paths.get("attacks", "cwe-78", "evidence", phase);
        Files.createDirectories(evidenceDir);

        String request = "POST /services/" + SERVICE_ID + "/documents/bundle\n"
                + "files=" + ServiceDocumentBundleService.SERVICE_OVERVIEW + "\n"
                + "files=" + ATTACK_ARGUMENT + "\n";
        Files.write(evidenceDir.resolve("request.txt"), request.getBytes(StandardCharsets.UTF_8));

        String headers = "status=" + result.getResponse().getStatus() + "\n"
                + "Content-Type=" + valueOrEmpty(result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE)) + "\n"
                + "Content-Disposition=" + valueOrEmpty(
                        result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)) + "\n";
        Files.write(evidenceDir.resolve("response-headers.txt"), headers.getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("response.tar"), responseBody);

        StringBuilder contents = new StringBuilder();
        for (String member : members) {
            contents.append(member).append('\n');
        }
        Files.write(evidenceDir.resolve("tar-contents.txt"),
                contents.toString().getBytes(StandardCharsets.UTF_8));

        String resultText = "httpStatus=" + result.getResponse().getStatus() + "\n"
                + "markerPresent=" + markerPresent + "\n";
        Files.write(evidenceDir.resolve("result.txt"), resultText.getBytes(StandardCharsets.UTF_8));
    }

    private void printEvidence(String phase,
                               MvcResult result,
                               List<String> members,
                               boolean markerPresent) {
        System.out.println("CWE78_EVIDENCE_BEGIN phase=" + phase);
        System.out.println("httpStatus=" + result.getResponse().getStatus());
        System.out.println("tarMembers=" + members);
        System.out.println("markerPresent=" + markerPresent);
        System.out.println("CWE78_EVIDENCE_END");
    }

    private Path writeTemporaryArchive(byte[] bytes) throws Exception {
        Path archive = Files.createTempFile(tempDirectory, "cwe78-response-", ".tar");
        Files.write(archive, bytes);
        return archive;
    }

    private List<String> listArchiveMembers(Path archive) throws Exception {
        Process process = new ProcessBuilder("tar", "-tf", archive.toString())
                .redirectErrorStream(true)
                .start();
        List<String> members = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = normalizeArchiveMember(line.trim());
                if (!normalized.isEmpty()) {
                    members.add(normalized);
                }
            }
        }
        assertEquals(0, process.waitFor(), "returned response must be a readable TAR archive");
        return members;
    }

    private void createPersistentArchive() throws Exception {
        Path serviceDirectory = storage.serviceDirectory(SERVICE_ID);
        Files.createDirectories(serviceDirectory);
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW),
                "overview".getBytes(StandardCharsets.UTF_8));
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.PARTS_DETAILED),
                "parts".getBytes(StandardCharsets.UTF_8));
        Files.write(serviceDirectory.resolve(ServiceDocumentBundleService.WORK_DETAILED),
                "work".getBytes(StandardCharsets.UTF_8));

        Process process = new ProcessBuilder(
                "tar", "-czf", storage.serviceArchive(SERVICE_ID).toString(), "--",
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.PARTS_DETAILED,
                ServiceDocumentBundleService.WORK_DETAILED)
                .directory(serviceDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        assertEquals(0, process.waitFor(), "fixture archive creation must succeed");

        Files.delete(serviceDirectory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW));
        Files.delete(serviceDirectory.resolve(ServiceDocumentBundleService.PARTS_DETAILED));
        Files.delete(serviceDirectory.resolve(ServiceDocumentBundleService.WORK_DETAILED));
    }

    private Service completedService() {
        return new Service(
                SERVICE_ID,
                CUSTOMER_ID,
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

    private String normalizeArchiveMember(String member) {
        if (".".equals(member) || "./".equals(member)) {
            return "";
        }
        return member.startsWith("./") ? member.substring(2) : member;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
