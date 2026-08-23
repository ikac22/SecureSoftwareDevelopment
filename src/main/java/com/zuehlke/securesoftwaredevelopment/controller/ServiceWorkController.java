package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceType;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ServiceWorkController {
    private final ServiceWorkService serviceWorkService;

    public ServiceWorkController(ServiceWorkService serviceWorkService) {
        this.serviceWorkService = serviceWorkService;
    }

    @PostMapping("/catalog/service-types/search")
    public List<ServiceType> searchServiceTypes(
            @RequestBody ServiceWorkRequests.ServiceTypeSearch request) {
        return serviceWorkService.searchServiceTypes(request == null ? null : request.getQuery());
    }

    @PostMapping("/catalog/parts/search")
    public List<PartCatalogItem> searchParts(@RequestBody ServiceWorkRequests.PartSearch request) {
        return serviceWorkService.searchParts(request == null ? null : request.getFilters());
    }

    @PostMapping("/catalog/service-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceType createServiceType(@RequestBody ServiceType serviceType) {
        return serviceWorkService.createServiceType(serviceType);
    }

    @PostMapping("/catalog/parts")
    @ResponseStatus(HttpStatus.CREATED)
    public PartCatalogItem createPart(@RequestBody PartCatalogItem part) {
        return serviceWorkService.createPart(part);
    }

    @GetMapping("/services/{serviceId}/work")
    public ServiceDetails getWork(@PathVariable int serviceId) {
        return serviceWorkService.getForDisplay(serviceId);
    }

    @PostMapping("/services/{serviceId}/work/services/from-catalog")
    public ServiceDetails addService(@PathVariable int serviceId,
                                     @RequestBody ServiceWorkRequests.CatalogSelection request) {
        return serviceWorkService.addServiceFromCatalog(
                serviceId, request.getCatalogId(), request.getVersion());
    }

    @PostMapping("/services/{serviceId}/work/services/new")
    public ServiceDetails createAndAddService(@PathVariable int serviceId,
                                              @RequestBody ServiceWorkRequests.NewServiceType request) {
        return serviceWorkService.createAndAddService(
                serviceId, request.getServiceType(), request.getVersion());
    }

    @PutMapping("/services/{serviceId}/work/services/{itemId}")
    public ServiceDetails updateService(@PathVariable int serviceId,
                                        @PathVariable String itemId,
                                        @RequestBody ServiceWorkRequests.PerformedServiceUpdate request) {
        return serviceWorkService.updatePerformedService(
                serviceId, itemId, request.getVersion(), request.getLaborPrice(),
                request.getComment(), request.getSubServices());
    }

    @DeleteMapping("/services/{serviceId}/work/services/{itemId}")
    public ServiceDetails deleteService(@PathVariable int serviceId,
                                        @PathVariable String itemId,
                                        @RequestBody ServiceWorkRequests.CatalogSelection request) {
        return serviceWorkService.removePerformedService(serviceId, itemId, request.getVersion());
    }

    @PostMapping("/services/{serviceId}/work/services/{itemId}/parts/from-catalog")
    public ServiceDetails addPart(@PathVariable int serviceId,
                                  @PathVariable String itemId,
                                  @RequestBody ServiceWorkRequests.PartSelection request) {
        return serviceWorkService.addPartFromCatalog(
                serviceId, itemId, request.getCatalogId(), request.getQuantity(), request.getVersion());
    }

    @PostMapping("/services/{serviceId}/work/services/{itemId}/parts/new")
    public ServiceDetails createAndAddPart(@PathVariable int serviceId,
                                           @PathVariable String itemId,
                                           @RequestBody ServiceWorkRequests.NewPart request) {
        return serviceWorkService.createAndAddPart(
                serviceId, itemId, request.getPart(), request.getQuantity(), request.getVersion());
    }

    @PutMapping("/services/{serviceId}/work/services/{itemId}/parts/{partItemId}")
    public ServiceDetails updatePart(@PathVariable int serviceId,
                                     @PathVariable String itemId,
                                     @PathVariable String partItemId,
                                     @RequestBody ServiceWorkRequests.UsedPartUpdate request) {
        return serviceWorkService.updateUsedPart(
                serviceId, itemId, partItemId, request.getVersion(), request.getQuantity(),
                request.getUnitPrice(), request.getComment());
    }

    @DeleteMapping("/services/{serviceId}/work/services/{itemId}/parts/{partItemId}")
    public ServiceDetails deletePart(@PathVariable int serviceId,
                                     @PathVariable String itemId,
                                     @PathVariable String partItemId,
                                     @RequestBody ServiceWorkRequests.CatalogSelection request) {
        return serviceWorkService.removeUsedPart(serviceId, itemId, partItemId, request.getVersion());
    }
}
