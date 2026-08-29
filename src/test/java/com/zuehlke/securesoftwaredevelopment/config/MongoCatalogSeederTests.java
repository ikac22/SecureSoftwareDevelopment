package com.zuehlke.securesoftwaredevelopment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceType;
import com.zuehlke.securesoftwaredevelopment.repository.PartCatalogRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceDetailsRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceTypeRepository;
import com.zuehlke.securesoftwaredevelopment.service.ServicePriceCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoCatalogSeederTests {
    @Test
    void loadsAllResourcesInReferenceOrderAndDoesNotDuplicateData() {
        PartCatalogRepository partRepository = mock(PartCatalogRepository.class);
        ServiceTypeRepository serviceTypeRepository = mock(ServiceTypeRepository.class);
        ServiceDetailsRepository detailsRepository = mock(ServiceDetailsRepository.class);
        ServiceRepository sqlRepository = mock(ServiceRepository.class);
        Map<String, PartCatalogItem> parts = new LinkedHashMap<>();
        Map<String, ServiceType> serviceTypes = new LinkedHashMap<>();
        Map<Integer, ServiceDetails> serviceDetails = new LinkedHashMap<>();

        when(partRepository.existsById(anyString())).thenAnswer(invocation ->
                parts.containsKey(invocation.getArgument(0)));
        when(partRepository.save(any(PartCatalogItem.class))).thenAnswer(invocation -> {
            PartCatalogItem part = invocation.getArgument(0);
            parts.put(part.getId(), part);
            return part;
        });
        when(serviceTypeRepository.existsById(anyString())).thenAnswer(invocation ->
                serviceTypes.containsKey(invocation.getArgument(0)));
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(invocation -> {
            ServiceType type = invocation.getArgument(0);
            serviceTypes.put(type.getId(), type);
            return type;
        });
        when(detailsRepository.findByServiceId(anyInt())).thenAnswer(invocation ->
                Optional.ofNullable(serviceDetails.get(invocation.getArgument(0))));
        when(detailsRepository.save(any(ServiceDetails.class))).thenAnswer(invocation -> {
            ServiceDetails details = invocation.getArgument(0);
            serviceDetails.put(details.getServiceId(), details);
            return details;
        });
        when(sqlRepository.findById(2)).thenReturn(Optional.of(sqlService(2, ServiceStatus.IN_PROGRESS)));
        when(sqlRepository.findById(3)).thenReturn(Optional.of(sqlService(3, ServiceStatus.COMPLETED)));
        when(sqlRepository.findById(4)).thenReturn(Optional.of(sqlService(4, ServiceStatus.COMPLETED)));

        MongoCatalogSeeder seeder = new MongoCatalogSeeder(
                new ObjectMapper().findAndRegisterModules(),
                new DefaultResourceLoader(),
                partRepository,
                serviceTypeRepository,
                detailsRepository,
                sqlRepository,
                new ServicePriceCalculator()
        );

        seeder.run(mock(ApplicationArguments.class));
        seeder.run(mock(ApplicationArguments.class));

        assertThat(parts).hasSize(5);
        assertThat(serviceTypes).hasSize(4);
        assertThat(serviceDetails).hasSize(3);
        assertThat(serviceDetails.get(2).getTotalPrice()).isEqualByComparingTo("11400.00");
        assertThat(serviceDetails.get(3).getTotalPrice()).isEqualByComparingTo("10000.00");
        assertThat(serviceDetails.get(4).getTotalPrice()).isEqualByComparingTo("11500.00");
        verify(partRepository, times(5)).save(any(PartCatalogItem.class));
        verify(serviceTypeRepository, times(4)).save(any(ServiceType.class));
        verify(detailsRepository, times(3)).save(any(ServiceDetails.class));
    }

    private Service sqlService(int id, ServiceStatus status) {
        return new Service(id, 1, null, null, "Honda", "Repair",
                status, "marko.markovic", 60, null, null);
    }
}
