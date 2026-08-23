package com.zuehlke.securesoftwaredevelopment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ServiceDocumentStorage {
    private final Path root;

    public ServiceDocumentStorage(@Value("${app.service-documents.root:service-documents}") String root) {
        this(Paths.get(root));
    }

    ServiceDocumentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path serviceDirectory(int serviceId) {
        return root.resolve("service-" + serviceId).normalize();
    }
}
