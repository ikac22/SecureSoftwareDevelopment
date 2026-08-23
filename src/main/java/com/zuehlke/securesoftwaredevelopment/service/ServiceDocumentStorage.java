package com.zuehlke.securesoftwaredevelopment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ServiceDocumentStorage {
    public static final String ARCHIVE_NAME = "service-documents.tar.gz";

    private final Path root;

    public ServiceDocumentStorage(@Value("${app.service-documents.root:service-documents}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    public Path serviceDirectory(int serviceId) {
        return root.resolve("service-" + serviceId).normalize();
    }

    public Path serviceArchive(int serviceId) {
        return serviceDirectory(serviceId).resolve(ARCHIVE_NAME);
    }
}
