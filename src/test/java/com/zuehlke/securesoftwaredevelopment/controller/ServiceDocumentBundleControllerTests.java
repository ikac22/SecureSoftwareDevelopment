package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.service.ServiceDocumentBundleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDocumentBundleControllerTests {
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
}
