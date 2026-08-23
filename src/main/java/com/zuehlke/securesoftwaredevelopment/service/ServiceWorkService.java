package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceType;
import com.zuehlke.securesoftwaredevelopment.repository.PartCatalogRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceDetailsRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceTypeRepository;
import com.zuehlke.securesoftwaredevelopment.repository.VulnerablePartCatalogSearch;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@org.springframework.stereotype.Service
public class ServiceWorkService {
    private static final int NAME_LIMIT = 255;
    private static final int DESCRIPTION_LIMIT = 1000;
    private static final int COMMENT_LIMIT = 2000;

    private final ServiceRepository sqlServiceRepository;
    private final ServiceDetailsRepository serviceDetailsRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final PartCatalogRepository partCatalogRepository;
    private final VulnerablePartCatalogSearch vulnerablePartCatalogSearch;
    private final ServicePriceCalculator priceCalculator;

    public ServiceWorkService(ServiceRepository sqlServiceRepository,
                              ServiceDetailsRepository serviceDetailsRepository,
                              ServiceTypeRepository serviceTypeRepository,
                              PartCatalogRepository partCatalogRepository,
                              VulnerablePartCatalogSearch vulnerablePartCatalogSearch,
                              ServicePriceCalculator priceCalculator) {
        this.sqlServiceRepository = sqlServiceRepository;
        this.serviceDetailsRepository = serviceDetailsRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.partCatalogRepository = partCatalogRepository;
        this.vulnerablePartCatalogSearch = vulnerablePartCatalogSearch;
        this.priceCalculator = priceCalculator;
    }

    public ServiceDetails getForDisplay(int serviceId) {
        com.zuehlke.securesoftwaredevelopment.domain.Service sqlService = sqlService(serviceId);
        if (sqlService.getServiceStatus() == ServiceStatus.IN_PROGRESS) {
            return ensureStartedServiceDetails(serviceId);
        }
        if (sqlService.getServiceStatus() == ServiceStatus.COMPLETED) {
            return serviceDetailsRepository.findByServiceId(serviceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Completed service details not found"));
        }
        throw conflict("Work details are available only for in-progress or completed services");
    }

    public ServiceDetails ensureStartedServiceDetails(int serviceId) {
        requireStatus(serviceId, ServiceStatus.IN_PROGRESS);
        return serviceDetailsRepository.findByServiceId(serviceId).orElseGet(() -> {
            try {
                ServiceDetails details = new ServiceDetails(serviceId);
                details.setUpdatedAt(Instant.now());
                return serviceDetailsRepository.save(priceCalculator.calculate(details));
            } catch (DuplicateKeyException exception) {
                return serviceDetailsRepository.findByServiceId(serviceId).orElseThrow(() -> exception);
            }
        });
    }

    public List<ServiceType> searchServiceTypes(String query) {
        String effectiveQuery = query == null ? "" : query.trim();
        return serviceTypeRepository.findByNameContainingIgnoreCaseOrderByName(effectiveQuery);
    }

    public List<PartCatalogItem> searchParts(Map<String, Object> filters) {
        return vulnerablePartCatalogSearch.search(filters);
    }

    public ServiceType createServiceType(ServiceType candidate) {
        if (candidate == null) {
            throw badRequest("Service type is required");
        }
        candidate.setId(null);
        candidate.setName(requiredText(candidate.getName(), "Service type name", NAME_LIMIT));
        candidate.setDescription(requiredText(
                candidate.getDescription(), "Service type description", DESCRIPTION_LIMIT));
        candidate.setAverageLaborPrice(nonNegative(candidate.getAverageLaborPrice(), "Average labor price"));
        validateAttributes(candidate.getAttributes());
        candidate.setNormalizedName(CatalogNormalizer.serviceName(candidate.getName()));

        if (serviceTypeRepository.findByNormalizedName(candidate.getNormalizedName()).isPresent()) {
            throw conflict("A service type with this name already exists");
        }

        Set<String> subServiceNames = new HashSet<>();
        for (ServiceType.SubServiceTemplate subService : candidate.getDefaultSubServices()) {
            subService.setName(requiredText(subService.getName(), "Sub-service name", NAME_LIMIT));
            if (!subServiceNames.add(CatalogNormalizer.serviceName(subService.getName()))) {
                throw badRequest("Duplicate sub-service name");
            }
            if (subService.getTemplateId() == null || subService.getTemplateId().trim().isEmpty()) {
                subService.setTemplateId(uuid());
            }
        }

        Set<String> defaultPartIds = new HashSet<>();
        for (ServiceType.DefaultPart defaultPart : candidate.getDefaultParts()) {
            String partId = requiredText(defaultPart.getCatalogPartId(), "Catalog part ID", NAME_LIMIT);
            if (!defaultPartIds.add(partId)) {
                throw badRequest("Duplicate default part");
            }
            if (!partCatalogRepository.existsById(partId)) {
                throw badRequest("Unknown default part " + partId);
            }
            defaultPart.setDefaultQuantity(positive(defaultPart.getDefaultQuantity(), "Default part quantity"));
        }

        try {
            return serviceTypeRepository.save(candidate);
        } catch (DuplicateKeyException exception) {
            throw conflict("A service type with this name already exists");
        }
    }

    public PartCatalogItem createPart(PartCatalogItem candidate) {
        if (candidate == null) {
            throw badRequest("Part is required");
        }
        candidate.setId(null);
        candidate.setName(requiredText(candidate.getName(), "Part name", NAME_LIMIT));
        candidate.setUnit(requiredText(candidate.getUnit(), "Part unit", 50));
        candidate.setDescription(optionalText(candidate.getDescription(), DESCRIPTION_LIMIT));
        candidate.setManufacturer(optionalText(candidate.getManufacturer(), NAME_LIMIT));
        candidate.setPartNumber(optionalText(candidate.getPartNumber(), NAME_LIMIT));
        candidate.setAverageUnitPrice(nonNegative(candidate.getAverageUnitPrice(), "Average unit price"));
        validateAttributes(candidate.getAttributes());
        candidate.setNormalizedKey(CatalogNormalizer.partKey(
                candidate.getName(), candidate.getManufacturer(), candidate.getPartNumber()));

        if (partCatalogRepository.findByNormalizedKey(candidate.getNormalizedKey()).isPresent()) {
            throw conflict("A part with this name, manufacturer and part number already exists");
        }

        try {
            return partCatalogRepository.save(candidate);
        } catch (DuplicateKeyException exception) {
            throw conflict("A part with this name, manufacturer and part number already exists");
        }
    }

    public ServiceDetails addServiceFromCatalog(int serviceId, String catalogServiceId, Long expectedVersion) {
        ServiceDetails details = mutableDetails(serviceId, expectedVersion);
        ServiceType catalogService = serviceTypeRepository.findById(catalogServiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service type not found"));

        boolean alreadyAdded = details.getPerformedServices().stream()
                .anyMatch(item -> catalogServiceId.equals(item.getCatalogServiceId()));
        if (alreadyAdded) {
            throw conflict("This service type is already present on the service");
        }

        details.getPerformedServices().add(snapshot(catalogService));
        return save(details);
    }

    public ServiceDetails createAndAddService(int serviceId, ServiceType candidate, Long expectedVersion) {
        mutableDetails(serviceId, expectedVersion);
        ServiceType created = createServiceType(candidate);
        return addServiceFromCatalog(serviceId, created.getId(), expectedVersion);
    }

    public ServiceDetails updatePerformedService(int serviceId, String itemId, Long expectedVersion,
                                                 BigDecimal laborPrice, String comment,
                                                 List<String> subServiceNames) {
        ServiceDetails details = mutableDetails(serviceId, expectedVersion);
        ServiceDetails.PerformedService performedService = performedService(details, itemId);
        performedService.setLaborPrice(nonNegative(laborPrice, "Labor price"));
        performedService.setComment(optionalText(comment, COMMENT_LIMIT));
        performedService.setSubServices(mergeSubServices(
                performedService.getSubServices(), subServiceNames));
        return save(details);
    }

    public ServiceDetails removePerformedService(int serviceId, String itemId, Long expectedVersion) {
        ServiceDetails details = mutableDetails(serviceId, expectedVersion);
        boolean removed = details.getPerformedServices().removeIf(item -> itemId.equals(item.getItemId()));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Performed service not found");
        }
        return save(details);
    }

    public ServiceDetails addPartFromCatalog(int serviceId, String serviceItemId, String catalogPartId,
                                             BigDecimal quantity, Long expectedVersion) {
        ServiceDetails details = mutableDetails(serviceId, expectedVersion);
        ServiceDetails.PerformedService performedService = performedService(details, serviceItemId);
        if (performedService.getUsedParts().stream()
                .anyMatch(part -> catalogPartId.equals(part.getCatalogPartId()))) {
            throw conflict("This part is already present on the performed service");
        }
        PartCatalogItem catalogPart = partCatalogRepository.findById(catalogPartId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog part not found"));
        performedService.getUsedParts().add(snapshot(catalogPart, positive(quantity, "Part quantity")));
        return save(details);
    }

    public ServiceDetails createAndAddPart(int serviceId, String serviceItemId, PartCatalogItem candidate,
                                           BigDecimal quantity, Long expectedVersion) {
        ServiceDetails current = mutableDetails(serviceId, expectedVersion);
        performedService(current, serviceItemId);
        PartCatalogItem created = createPart(candidate);
        return addPartFromCatalog(serviceId, serviceItemId, created.getId(), quantity, expectedVersion);
    }

    public ServiceDetails updateUsedPart(int serviceId, String serviceItemId, String partItemId,
                                         Long expectedVersion, BigDecimal quantity,
                                         BigDecimal unitPrice, String comment) {
        ServiceDetails details = mutableDetails(serviceId, expectedVersion);
        ServiceDetails.UsedPart usedPart = usedPart(performedService(details, serviceItemId), partItemId);
        usedPart.setQuantity(positive(quantity, "Part quantity"));
        usedPart.setUnitPrice(nonNegative(unitPrice, "Part unit price"));
        usedPart.setComment(optionalText(comment, COMMENT_LIMIT));
        return save(details);
    }

    public ServiceDetails removeUsedPart(int serviceId, String serviceItemId, String partItemId,
                                         Long expectedVersion) {
        ServiceDetails details = mutableDetails(serviceId, expectedVersion);
        ServiceDetails.PerformedService performedService = performedService(details, serviceItemId);
        boolean removed = performedService.getUsedParts().removeIf(part -> partItemId.equals(part.getItemId()));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Used part not found");
        }
        return save(details);
    }

    public ServiceDetails prepareForCompletion(int serviceId) {
        ServiceDetails details = mutableDetails(serviceId, null);
        if (details.getPerformedServices().isEmpty()) {
            throw conflict("Add at least one performed service before completing the service");
        }
        return save(details);
    }

    private ServiceDetails.PerformedService snapshot(ServiceType source) {
        ServiceDetails.PerformedService snapshot = new ServiceDetails.PerformedService();
        snapshot.setItemId(uuid());
        snapshot.setCatalogServiceId(source.getId());
        snapshot.setName(source.getName());
        snapshot.setDescription(source.getDescription());
        snapshot.setAttributes(copy(source.getAttributes()));
        snapshot.setLaborPrice(source.getAverageLaborPrice());

        List<ServiceDetails.PerformedSubService> subServices = new ArrayList<>();
        for (ServiceType.SubServiceTemplate sourceSubService : source.getDefaultSubServices()) {
            subServices.add(new ServiceDetails.PerformedSubService(uuid(), sourceSubService.getName()));
        }
        snapshot.setSubServices(subServices);

        List<ServiceDetails.UsedPart> usedParts = new ArrayList<>();
        for (ServiceType.DefaultPart defaultPart : source.getDefaultParts()) {
            PartCatalogItem part = partCatalogRepository.findById(defaultPart.getCatalogPartId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Service type references missing catalog part " + defaultPart.getCatalogPartId()));
            usedParts.add(snapshot(part, defaultPart.getDefaultQuantity()));
        }
        snapshot.setUsedParts(usedParts);
        return snapshot;
    }

    private ServiceDetails.UsedPart snapshot(PartCatalogItem source, BigDecimal quantity) {
        ServiceDetails.UsedPart snapshot = new ServiceDetails.UsedPart();
        snapshot.setItemId(uuid());
        snapshot.setCatalogPartId(source.getId());
        snapshot.setName(source.getName());
        snapshot.setDescription(source.getDescription());
        snapshot.setPartNumber(source.getPartNumber());
        snapshot.setManufacturer(source.getManufacturer());
        snapshot.setUnit(source.getUnit());
        snapshot.setAttributes(copy(source.getAttributes()));
        snapshot.setQuantity(quantity);
        snapshot.setUnitPrice(source.getAverageUnitPrice());
        return snapshot;
    }

    private List<ServiceDetails.PerformedSubService> mergeSubServices(
            List<ServiceDetails.PerformedSubService> existing, List<String> requestedNames) {
        Map<String, ServiceDetails.PerformedSubService> existingByName = new HashMap<>();
        for (ServiceDetails.PerformedSubService current : existing) {
            existingByName.put(CatalogNormalizer.serviceName(current.getName()), current);
        }

        List<ServiceDetails.PerformedSubService> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (requestedNames == null) {
            return result;
        }
        for (String requestedName : requestedNames) {
            String name = requiredText(requestedName, "Sub-service name", NAME_LIMIT);
            String normalized = CatalogNormalizer.serviceName(name);
            if (!names.add(normalized)) {
                throw badRequest("Duplicate sub-service name");
            }
            ServiceDetails.PerformedSubService current = existingByName.get(normalized);
            result.add(current == null
                    ? new ServiceDetails.PerformedSubService(uuid(), name)
                    : new ServiceDetails.PerformedSubService(current.getItemId(), name));
        }
        return result;
    }

    private ServiceDetails mutableDetails(int serviceId, Long expectedVersion) {
        requireStatus(serviceId, ServiceStatus.IN_PROGRESS);
        ServiceDetails details = ensureStartedServiceDetails(serviceId);
        if (expectedVersion != null && !Objects.equals(expectedVersion, details.getVersion())) {
            throw conflict("Service details were changed in another editor; reload and try again");
        }
        return details;
    }

    private ServiceDetails save(ServiceDetails details) {
        details.setUpdatedAt(Instant.now());
        try {
            return serviceDetailsRepository.save(priceCalculator.calculate(details));
        } catch (OptimisticLockingFailureException exception) {
            throw conflict("Service details were changed in another editor; reload and try again");
        }
    }

    private ServiceDetails.PerformedService performedService(ServiceDetails details, String itemId) {
        return details.getPerformedServices().stream()
                .filter(item -> itemId.equals(item.getItemId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Performed service not found"));
    }

    private ServiceDetails.UsedPart usedPart(ServiceDetails.PerformedService performedService,
                                             String partItemId) {
        return performedService.getUsedParts().stream()
                .filter(part -> partItemId.equals(part.getItemId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Used part not found"));
    }

    private com.zuehlke.securesoftwaredevelopment.domain.Service sqlService(int serviceId) {
        return sqlServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    private void requireStatus(int serviceId, ServiceStatus required) {
        if (sqlService(serviceId).getServiceStatus() != required) {
            throw conflict("Service work can be changed only while the service is in progress");
        }
    }

    private String requiredText(String value, String field, int limit) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > limit) {
            throw badRequest(field + " is required and must be at most " + limit + " characters");
        }
        return normalized;
    }

    private String optionalText(String value, int limit) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > limit) {
            throw badRequest("Text must be at most " + limit + " characters");
        }
        return normalized;
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw badRequest(field + " must be zero or greater");
        }
        return value;
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw badRequest(field + " must be greater than zero");
        }
        return value;
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private void validateAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
                throw badRequest("Attribute keys may contain only letters, digits, underscores and dashes");
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof String)
                    && !(value instanceof Number) && !(value instanceof Boolean)) {
                throw badRequest("Attribute values must be strings, numbers or booleans");
            }
        }
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
