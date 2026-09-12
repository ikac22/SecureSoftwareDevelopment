package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.service.ServiceHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class ServiceHistoryController {
    private final ServiceHistoryService serviceHistoryService;

    public ServiceHistoryController(ServiceHistoryService serviceHistoryService) {
        this.serviceHistoryService = serviceHistoryService;
    }

    @GetMapping("/my-finished-services")
    public String showHistory(Authentication authentication) {
        authenticatedCustomer(authentication);
        return "my-finished-services";
    }

    @PostMapping("/api/my/service-history/search")
    @ResponseBody
    public List<ServiceDetails> searchHistory(@RequestBody(required = false) ServiceHistorySearch request,
                                              Authentication authentication) {
        User customer = authenticatedCustomer(authentication);
        ServiceHistorySearch search = request == null ? new ServiceHistorySearch() : request;
        return serviceHistoryService.search(
                customer.getId(),
                search.getCarModel(),
                search.getServiceName(),
                search.getPartName(),
                search.getTechnician(),
                search.isShowPerformedServices(),
                search.isShowUsedParts());
    }

    private User authenticatedCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account required");
        }
        return (User) authentication.getPrincipal();
    }

    public static class ServiceHistorySearch {
        private String carModel;
        private String serviceName;
        private String partName;
        private String technician;
        private boolean showPerformedServices;
        private boolean showUsedParts;

        public String getCarModel() {
            return carModel;
        }

        public void setCarModel(String carModel) {
            this.carModel = carModel;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getPartName() {
            return partName;
        }

        public void setPartName(String partName) {
            this.partName = partName;
        }

        public String getTechnician() {
            return technician;
        }

        public void setTechnician(String technician) {
            this.technician = technician;
        }

        public boolean isShowPerformedServices() {
            return showPerformedServices;
        }

        public void setShowPerformedServices(boolean showPerformedServices) {
            this.showPerformedServices = showPerformedServices;
        }

        public boolean isShowUsedParts() {
            return showUsedParts;
        }

        public void setShowUsedParts(boolean showUsedParts) {
            this.showUsedParts = showUsedParts;
        }
    }
}
