package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ServiceDocumentBundleService {
    public static final String SERVICE_OVERVIEW = "service-overview.pdf";
    public static final String PARTS_DETAILED = "parts-detailed.pdf";
    public static final String WORK_DETAILED = "work-detailed.pdf";

    private static final int MAX_SELECTED_FILES = 3;
    private static final int MAX_FILE_NAME_LENGTH = 96;
    private static final Set<String> ALLOWED_DOCUMENTS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(SERVICE_OVERVIEW, PARTS_DETAILED, WORK_DETAILED)));

    private final ServiceRepository serviceRepository;
    private final ServiceDocumentStorage documentStorage;
    private final boolean safeBundling;

    public ServiceDocumentBundleService(ServiceRepository serviceRepository,
                                        ServiceDocumentStorage documentStorage,
                                        @Value("${app.service-documents.safe-bundling:false}") boolean safeBundling) {
        this.serviceRepository = serviceRepository;
        this.documentStorage = documentStorage;
        this.safeBundling = safeBundling;
    }

    public byte[] createBundle(int serviceId, int customerId, List<String> requestedFiles)
            throws IOException, InterruptedException {
        assertCompletedServiceOwnedByCustomer(serviceId, customerId);
        List<String> selectedFiles = normalizeSelection(requestedFiles);

        Path serviceDirectory = documentStorage.serviceDirectory(serviceId);
        if (!Files.isDirectory(serviceDirectory)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Service documents are not available yet");
        }

        Path archive = Files.createTempFile("service-" + serviceId + "-documents-", ".tar");
        try {
            List<String> command = new ArrayList<>();
            command.add("tar");
            command.add("-cf");
            command.add(archive.toString());
            if (safeBundling) {
                command.add("--");
            }
            command.addAll(selectedFiles);

            Process process = new ProcessBuilder(command)
                    .directory(serviceDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();

            String processOutput = readOutput(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("tar failed with exit code " + exitCode + ": " + processOutput);
            }
            return Files.readAllBytes(archive);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private void assertCompletedServiceOwnedByCustomer(int serviceId, int customerId) {
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        if (service.getPersonId() == null || service.getPersonId() != customerId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found");
        }
        if (service.getServiceStatus() != ServiceStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Documents can only be downloaded for completed services");
        }
    }

    private List<String> normalizeSelection(List<String> requestedFiles) {
        if (requestedFiles == null || requestedFiles.isEmpty()) {
            throw invalidSelection("Select at least one document");
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String rawFileName : requestedFiles) {
            if (rawFileName == null) {
                throw invalidSelection("Invalid document name");
            }
            String fileName = rawFileName.trim();
            validateFileLikeShape(fileName);
            if (safeBundling && !ALLOWED_DOCUMENTS.contains(fileName)) {
                throw invalidSelection("Unknown service document");
            }
            selected.add(fileName);
            if (selected.size() > MAX_SELECTED_FILES) {
                throw invalidSelection("Too many documents selected");
            }
        }

        if (!safeBundling && selected.stream().noneMatch(ALLOWED_DOCUMENTS::contains)) {
            throw invalidSelection("At least one service document must be selected");
        }
        return new ArrayList<>(selected);
    }

    private void validateFileLikeShape(String fileName) {
        if (fileName.isEmpty()
                || fileName.length() > MAX_FILE_NAME_LENGTH
                || !fileName.endsWith(".pdf")
                || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0
                || ".".equals(fileName)
                || "..".equals(fileName)) {
            throw invalidSelection("Invalid document name");
        }
        for (int i = 0; i < fileName.length(); i++) {
            char ch = fileName.charAt(i);
            if (ch < 0x21 || ch > 0x7e) {
                throw invalidSelection("Invalid document name");
            }
        }
    }

    private String readOutput(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private ResponseStatusException invalidSelection(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
