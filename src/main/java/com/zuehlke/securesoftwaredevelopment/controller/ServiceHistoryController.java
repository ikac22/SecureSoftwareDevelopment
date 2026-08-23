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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return serviceHistoryService.search(
                customer.getId(),
                request == null ? null : request.getFilters());
    }

    private User authenticatedCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account required");
        }
        return (User) authentication.getPrincipal();
    }

    public static class ServiceHistorySearch {
        private Map<String, Object> filters = new LinkedHashMap<>();

        public Map<String, Object> getFilters() {
            return filters;
        }

        public void setFilters(Map<String, Object> filters) {
            this.filters = filters == null ? new LinkedHashMap<>() : filters;
        }
    }
}
