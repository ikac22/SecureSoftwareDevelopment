package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.service.ServiceDocumentBundleService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/services/{serviceId}/documents")
public class ServiceDocumentBundleController {
    private final ServiceDocumentBundleService bundleService;

    public ServiceDocumentBundleController(ServiceDocumentBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @PostMapping("/bundle")
    @ResponseBody
    public ResponseEntity<byte[]> downloadBundle(@PathVariable int serviceId,
                                                 @RequestParam(name = "files", required = false)
                                                 List<String> files,
                                                 Authentication authentication) throws IOException {
        User customer = authenticatedCustomer(authentication);
        try {
            byte[] archive = bundleService.createBundle(serviceId, customer.getId(), files);
            String fileName = "service-" + serviceId + "-documents.tar";
            String disposition = ContentDisposition.builder("attachment")
                    .filename(fileName)
                    .build()
                    .toString();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType("application/x-tar"))
                    .contentLength(archive.length)
                    .body(archive);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Document bundle creation was interrupted", exception);
        }
    }

    private User authenticatedCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account required");
        }
        return (User) authentication.getPrincipal();
    }
}
