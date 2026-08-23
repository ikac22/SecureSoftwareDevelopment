package com.zuehlke.securesoftwaredevelopment.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceType;
import com.zuehlke.securesoftwaredevelopment.repository.PartCatalogRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceDetailsRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceTypeRepository;
import com.zuehlke.securesoftwaredevelopment.service.ServicePriceCalculator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.mongodb.seed-enabled", havingValue = "true", matchIfMissing = true)
public class MongoCatalogSeeder implements ApplicationRunner {
    private static final String PARTS = "classpath:mongo/parts.json";
    private static final String SERVICE_TYPES = "classpath:mongo/service-types.json";
    private static final String SERVICE_DETAILS = "classpath:mongo/service-details.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final PartCatalogRepository partCatalogRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceDetailsRepository serviceDetailsRepository;
    private final ServiceRepository sqlServiceRepository;
    private final ServicePriceCalculator priceCalculator;

    public MongoCatalogSeeder(ObjectMapper objectMapper,
                              ResourceLoader resourceLoader,
                              PartCatalogRepository partCatalogRepository,
                              ServiceTypeRepository serviceTypeRepository,
                              ServiceDetailsRepository serviceDetailsRepository,
                              ServiceRepository sqlServiceRepository,
                              ServicePriceCalculator priceCalculator) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.partCatalogRepository = partCatalogRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceDetailsRepository = serviceDetailsRepository;
        this.sqlServiceRepository = sqlServiceRepository;
        this.priceCalculator = priceCalculator;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<PartCatalogItem> parts = read(PARTS, new TypeReference<List<PartCatalogItem>>() { });
        for (PartCatalogItem part : parts) {
            normalize(part);
            if (!partCatalogRepository.existsById(requiredId(part.getId(), PARTS))) {
                partCatalogRepository.save(part);
            }
        }

        List<ServiceType> serviceTypes = read(
                SERVICE_TYPES, new TypeReference<List<ServiceType>>() { });
        for (ServiceType serviceType : serviceTypes) {
            normalize(serviceType);
            validatePartReferences(serviceType);
            if (!serviceTypeRepository.existsById(requiredId(serviceType.getId(), SERVICE_TYPES))) {
                serviceTypeRepository.save(serviceType);
            }
        }

        List<ServiceDetails> serviceDetails = read(
                SERVICE_DETAILS, new TypeReference<List<ServiceDetails>>() { });
        for (ServiceDetails details : serviceDetails) {
            validateSqlService(details);
            if (!serviceDetailsRepository.findByServiceId(details.getServiceId()).isPresent()) {
                details.setUpdatedAt(Instant.now());
                serviceDetailsRepository.save(priceCalculator.calculate(details));
            }
        }
    }

    private void validatePartReferences(ServiceType serviceType) {
        for (ServiceType.DefaultPart defaultPart : serviceType.getDefaultParts()) {
            if (defaultPart.getCatalogPartId() == null
                    || !partCatalogRepository.existsById(defaultPart.getCatalogPartId())) {
                throw new IllegalStateException("Unknown catalog part " + defaultPart.getCatalogPartId()
                        + " referenced by service type " + serviceType.getId());
            }
            if (defaultPart.getDefaultQuantity() == null
                    || defaultPart.getDefaultQuantity().signum() <= 0) {
                throw new IllegalStateException("Default part quantity must be positive for "
                        + serviceType.getId());
            }
        }
    }

    private void validateSqlService(ServiceDetails details) {
        if (details.getServiceId() == null) {
            throw new IllegalStateException("service-details.json contains a document without serviceId");
        }
        com.zuehlke.securesoftwaredevelopment.domain.Service sqlService = sqlServiceRepository
                .findById(details.getServiceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown SQL service " + details.getServiceId() + " in service-details.json"));
        if (sqlService.getServiceStatus() != ServiceStatus.IN_PROGRESS
                && sqlService.getServiceStatus() != ServiceStatus.COMPLETED) {
            throw new IllegalStateException("Seeded work details require IN_PROGRESS or COMPLETED service "
                    + details.getServiceId());
        }
    }

    private void normalize(PartCatalogItem part) {
        requireText(part.getName(), "Part name");
        requireText(part.getUnit(), "Part unit");
        if (part.getAverageUnitPrice() == null || part.getAverageUnitPrice().signum() < 0) {
            throw new IllegalStateException("Part averageUnitPrice must be zero or greater");
        }
        part.setNormalizedKey(normalize(part.getName()) + "|"
                + normalize(part.getManufacturer()) + "|" + normalize(part.getPartNumber()));
    }

    private void normalize(ServiceType serviceType) {
        requireText(serviceType.getName(), "Service type name");
        requireText(serviceType.getDescription(), "Service type description");
        if (serviceType.getAverageLaborPrice() == null
                || serviceType.getAverageLaborPrice().signum() < 0) {
            throw new IllegalStateException("Service averageLaborPrice must be zero or greater");
        }
        serviceType.setNormalizedName(normalize(serviceType.getName()));
    }

    private String requiredId(String id, String source) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalStateException(source + " contains a document without a stable _id");
        }
        return id;
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(field + " is required in Mongo seed data");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private <T> T read(String location, TypeReference<T> typeReference) {
        Resource resource = resourceLoader.getResource(location);
        try {
            return objectMapper.readValue(resource.getInputStream(), typeReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Mongo seed resource " + location, exception);
        }
    }
}
