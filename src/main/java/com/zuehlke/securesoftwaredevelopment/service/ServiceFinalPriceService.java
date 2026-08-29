package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.repository.PersonRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceDetailsRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class ServiceFinalPriceService {
    private final ServiceRepository serviceRepository;
    private final ServiceDetailsRepository serviceDetailsRepository;
    private final PersonRepository personRepository;
    private final ServicePricingPolicyEvaluator pricingPolicyEvaluator;

    public ServiceFinalPriceService(ServiceRepository serviceRepository,
                                    ServiceDetailsRepository serviceDetailsRepository,
                                    PersonRepository personRepository,
                                    ServicePricingPolicyEvaluator pricingPolicyEvaluator) {
        this.serviceRepository = serviceRepository;
        this.serviceDetailsRepository = serviceDetailsRepository;
        this.personRepository = personRepository;
        this.pricingPolicyEvaluator = pricingPolicyEvaluator;
    }

    public ServiceDetails apply(int serviceId) {
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        ServiceDetails details = serviceDetailsRepository.findByServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service details not found"));
        Person customer = personRepository.get(service.getPersonId());
        if (customer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }

        int completedServices = serviceRepository.countCompletedByPersonId(service.getPersonId());
        BigDecimal basePrice = details.getTotalPrice();
        BigDecimal finalPrice = pricingPolicyEvaluator.evaluate(
                details, service, customer, completedServices);

        details.setTotalPrice(finalPrice);
        details.setPricingPolicy(new ServiceDetails.PricingPolicySnapshot(
                pricingPolicyEvaluator.policyTierForCompletedServices(completedServices),
                pricingPolicyEvaluator.policyResourceForCompletedServices(completedServices),
                basePrice,
                finalPrice
        ));
        details.setUpdatedAt(Instant.now());
        return serviceDetailsRepository.save(details);
    }
}
