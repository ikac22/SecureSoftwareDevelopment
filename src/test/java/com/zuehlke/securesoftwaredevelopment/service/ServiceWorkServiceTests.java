package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceType;
import com.zuehlke.securesoftwaredevelopment.repository.PartCatalogRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceDetailsRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceTypeRepository;
import com.zuehlke.securesoftwaredevelopment.repository.VulnerablePartCatalogSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceWorkServiceTests {
    private ServiceRepository sqlServiceRepository;
    private ServiceDetailsRepository serviceDetailsRepository;
    private ServiceTypeRepository serviceTypeRepository;
    private PartCatalogRepository partCatalogRepository;
    private VulnerablePartCatalogSearch vulnerableSearch;
    private ServiceWorkService serviceWorkService;

    @BeforeEach
    void setUp() {
        sqlServiceRepository = mock(ServiceRepository.class);
        serviceDetailsRepository = mock(ServiceDetailsRepository.class);
        serviceTypeRepository = mock(ServiceTypeRepository.class);
        partCatalogRepository = mock(PartCatalogRepository.class);
        vulnerableSearch = mock(VulnerablePartCatalogSearch.class);
        serviceWorkService = new ServiceWorkService(
                sqlServiceRepository,
                serviceDetailsRepository,
                serviceTypeRepository,
                partCatalogRepository,
                vulnerableSearch,
                new ServicePriceCalculator()
        );
        when(serviceDetailsRepository.save(any(ServiceDetails.class))).thenAnswer(invocation -> {
            ServiceDetails details = invocation.getArgument(0);
            details.setVersion(details.getVersion() == null ? 0L : details.getVersion() + 1);
            return details;
        });
    }

    @Test
    void addingCatalogServiceSnapshotsSubServicesPartsAndPrices() {
        ServiceDetails details = details(1, 3L);
        ServiceType serviceType = serviceType();
        PartCatalogItem part = part();
        when(sqlServiceRepository.findById(1)).thenReturn(Optional.of(sqlService(ServiceStatus.IN_PROGRESS)));
        when(serviceDetailsRepository.findByServiceId(1)).thenReturn(Optional.of(details));
        when(serviceTypeRepository.findById("brakes")).thenReturn(Optional.of(serviceType));
        when(partCatalogRepository.findById("pads")).thenReturn(Optional.of(part));

        ServiceDetails result = serviceWorkService.addServiceFromCatalog(1, "brakes", 3L);

        assertThat(result.getPerformedServices()).hasSize(1);
        ServiceDetails.PerformedService snapshot = result.getPerformedServices().get(0);
        assertThat(snapshot.getCatalogServiceId()).isEqualTo("brakes");
        assertThat(snapshot.getSubServices()).extracting(ServiceDetails.PerformedSubService::getName)
                .containsExactly("Front brake inspection", "Brake fluid check");
        assertThat(snapshot.getUsedParts()).hasSize(1);
        assertThat(snapshot.getUsedParts().get(0).getCatalogPartId()).isEqualTo("pads");
        assertThat(snapshot.getUsedParts().get(0).getLineTotal()).isEqualByComparingTo("7500.00");
        assertThat(snapshot.getTotalPrice()).isEqualByComparingTo("12500.00");
        assertThat(result.getTotalPrice()).isEqualByComparingTo("12500.00");

        serviceType.setName("Changed catalog name");
        part.setAverageUnitPrice(new BigDecimal("99999"));
        assertThat(snapshot.getName()).isEqualTo("Brake service");
        assertThat(snapshot.getUsedParts().get(0).getUnitPrice()).isEqualByComparingTo("7500.00");
    }

    @Test
    void editingPartRecalculatesCanonicalTotals() {
        ServiceDetails details = details(1, 4L);
        ServiceDetails.PerformedService service = new ServiceDetails.PerformedService();
        service.setItemId("service-item");
        service.setLaborPrice(new BigDecimal("1000"));
        ServiceDetails.UsedPart part = new ServiceDetails.UsedPart();
        part.setItemId("part-item");
        part.setCatalogPartId("pads");
        part.setQuantity(BigDecimal.ONE);
        part.setUnitPrice(new BigDecimal("500"));
        service.setUsedParts(Collections.singletonList(part));
        details.setPerformedServices(Collections.singletonList(service));
        when(sqlServiceRepository.findById(1)).thenReturn(Optional.of(sqlService(ServiceStatus.IN_PROGRESS)));
        when(serviceDetailsRepository.findByServiceId(1)).thenReturn(Optional.of(details));

        ServiceDetails result = serviceWorkService.updateUsedPart(
                1, "service-item", "part-item", 4L,
                new BigDecimal("2.5"), new BigDecimal("400"), "Adjusted usage");

        assertThat(result.getPerformedServices().get(0).getUsedParts().get(0).getLineTotal())
                .isEqualByComparingTo("1000.00");
        assertThat(result.getTotalPrice()).isEqualByComparingTo("2000.00");
    }

    @Test
    void serviceSpecificMutationIsRejectedOutsideInProgress() {
        when(sqlServiceRepository.findById(1)).thenReturn(Optional.of(sqlService(ServiceStatus.COMPLETED)));

        assertThatThrownBy(() -> serviceWorkService.removePerformedService(1, "item", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(serviceDetailsRepository, never()).save(any(ServiceDetails.class));
    }

    @Test
    void staleVersionIsRejectedBeforeMutation() {
        ServiceDetails details = details(1, 7L);
        when(sqlServiceRepository.findById(1)).thenReturn(Optional.of(sqlService(ServiceStatus.IN_PROGRESS)));
        when(serviceDetailsRepository.findByServiceId(1)).thenReturn(Optional.of(details));

        assertThatThrownBy(() -> serviceWorkService.removePerformedService(1, "item", 6L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void completingAnEmptyWorkDocumentIsRejected() {
        ServiceDetails details = details(1, 0L);
        when(sqlServiceRepository.findById(1)).thenReturn(Optional.of(sqlService(ServiceStatus.IN_PROGRESS)));
        when(serviceDetailsRepository.findByServiceId(1)).thenReturn(Optional.of(details));

        assertThatThrownBy(() -> serviceWorkService.prepareForCompletion(1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private ServiceDetails details(int serviceId, long version) {
        ServiceDetails details = new ServiceDetails(serviceId);
        details.setVersion(version);
        return details;
    }

    private Service sqlService(ServiceStatus status) {
        return new Service(1, 1, null, null, "Honda", "Repair",
                status, "marko.markovic", 60, null, null);
    }

    private ServiceType serviceType() {
        ServiceType serviceType = new ServiceType();
        serviceType.setId("brakes");
        serviceType.setName("Brake service");
        serviceType.setDescription("Brake system service");
        serviceType.setAverageLaborPrice(new BigDecimal("5000"));
        serviceType.setAttributes(new LinkedHashMap<>(Collections.singletonMap("system", "BRAKES")));
        serviceType.setDefaultSubServices(Arrays.asList(
                new ServiceType.SubServiceTemplate("front", "Front brake inspection"),
                new ServiceType.SubServiceTemplate("fluid", "Brake fluid check")
        ));
        serviceType.setDefaultParts(Collections.singletonList(
                new ServiceType.DefaultPart("pads", BigDecimal.ONE)
        ));
        return serviceType;
    }

    private PartCatalogItem part() {
        PartCatalogItem part = new PartCatalogItem();
        part.setId("pads");
        part.setName("Front brake pad set");
        part.setUnit("SET");
        part.setAverageUnitPrice(new BigDecimal("7500"));
        return part;
    }
}
