package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.ScheduleService;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.TechnicianAvailability;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkflowService;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class ServiceController {
    private final ServiceRepository serviceRepository;
    private final ServiceWorkflowService serviceWorkflowService;
    private final ServiceWorkService serviceWorkService;

    public ServiceController(ServiceRepository serviceRepository,
                             ServiceWorkflowService serviceWorkflowService,
                             ServiceWorkService serviceWorkService) {
        this.serviceRepository = serviceRepository;
        this.serviceWorkflowService = serviceWorkflowService;
        this.serviceWorkService = serviceWorkService;
    }

    @GetMapping("/scheduled-services")
    public String showServices(Authentication authentication, Model model) {
        User customer = authenticatedCustomer(authentication);
        model.addAttribute("scheduledServices", serviceRepository.findByPersonId(customer.getId()));
        return "scheduled-services";
    }

    @GetMapping("/schedule-service")
    public String showScheduleService() {
        return "schedule-service";
    }

    @PostMapping("/schedule-service")
    public String scheduleService(ScheduleService scheduleService, Authentication authentication) throws SQLException {
        if (scheduleService.getCarModel() == null || scheduleService.getCarModel().trim().isEmpty()
                || scheduleService.getDescription() == null
                || scheduleService.getDescription().trim().isEmpty()
                || scheduleService.getDescription().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Car model and a valid service description are required");
        }
        scheduleService.setCarModel(scheduleService.getCarModel().trim());
        scheduleService.setDescription(scheduleService.getDescription().trim());
        User user = authenticatedCustomer(authentication);
        serviceRepository.insertScheduledService(user.getId(), scheduleService);
        return "redirect:/scheduled-services";
    }

    @GetMapping("/services/{id}")
    public String showService(@PathVariable int id, Model model) {
        Service service = serviceWorkflowService.get(id);
        model.addAttribute("service", service);
        if (service.getServiceStatus() == com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus.IN_PROGRESS
                || service.getServiceStatus() == com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus.COMPLETED) {
            model.addAttribute("serviceDetails", serviceWorkService.getForDisplay(id));
        }
        return "service-details";
    }

    @GetMapping("/services/{id}/available-slots")
    @ResponseBody
    public List<TechnicianAvailability> availableSlots(
            @PathVariable int id,
            @RequestParam String date,
            @RequestParam int estimatedDurationMinutes) {
        return serviceWorkflowService.findAvailableSlots(
                id, parseDate(date), estimatedDurationMinutes);
    }

    @PostMapping("/services/{id}/assign")
    public String assignTechnician(@PathVariable int id,
                                   @RequestParam String technician,
                                   @RequestParam String date,
                                   @RequestParam String time,
                                   @RequestParam int estimatedDurationMinutes) {
        serviceWorkflowService.assignTechnician(
                id, technician, parseDate(date), parseTime(time), estimatedDurationMinutes);
        return "redirect:/services/" + id;
    }

    @GetMapping("/services/{id}/cancel")
    public String showCancelService(@PathVariable int id, Model model) {
        model.addAttribute("service", serviceWorkflowService.get(id));
        return "cancel-service";
    }

    @PostMapping("/services/{id}/cancel")
    public String cancelService(@PathVariable int id) {
        serviceWorkflowService.cancel(id);
        return "redirect:/services/" + id;
    }

    @PostMapping("/services/{id}/start")
    public String startService(@PathVariable int id) {
        serviceWorkflowService.start(id);
        return "redirect:/services/" + id;
    }

    @PostMapping("/services/{id}/complete")
    public String completeService(@PathVariable int id) {
        serviceWorkflowService.complete(id);
        return "redirect:/services/" + id;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid service date");
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid service start time");
        }
    }

    private User authenticatedCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account required");
        }
        return (User) authentication.getPrincipal();
    }
}
