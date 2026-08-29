package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceDetailsRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceHistorySearchRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Service
public class ServiceHistoryService {
    private final ServiceDetailsRepository serviceDetailsRepository;
    private final ServiceHistorySearchRepository serviceHistorySearchRepository;

    public ServiceHistoryService(ServiceDetailsRepository serviceDetailsRepository,
                                 ServiceHistorySearchRepository serviceHistorySearchRepository) {
        this.serviceDetailsRepository = serviceDetailsRepository;
        this.serviceHistorySearchRepository = serviceHistorySearchRepository;
    }

    public ServiceDetails captureCompletedService(Service service, ServiceDetails details) {
        details.setCustomerId(service.getPersonId());
        details.setCarModel(service.getCarModel());
        details.setServiceDescription(service.getDescription());
        details.setTechnician(service.getTechnician());
        details.setServiceDate(service.getDate() == null ? null : service.getDate().toString());
        details.setServiceTime(service.getTime() == null ? null : service.getTime().toString());
        details.setCompletedAt(Instant.now());
        details.setUpdatedAt(Instant.now());
        return serviceDetailsRepository.save(details);
    }

    public List<ServiceDetails> search(int authenticatedCustomerId,
                                       Map<String, Object> filters,
                                       Map<String, Object> view) {
        return serviceHistorySearchRepository.search(authenticatedCustomerId, filters, view);
    }
}
