package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.ScheduleService;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceTicket;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Controller
public class ServiceController {
    private final ServiceRepository serviceRepository;
    private final ServiceWorkflowService serviceWorkflowService;

    public ServiceController(ServiceRepository serviceRepository,
                             ServiceWorkflowService serviceWorkflowService) {
        this.serviceRepository = serviceRepository;
        this.serviceWorkflowService = serviceWorkflowService;
    }

    @GetMapping("/scheduled-services")
    public String showServices(@RequestParam(value = "columns", required = false, defaultValue = "firstName,lastName,carModel,date") String columns, Model model) {
        List<Service> scheduledServices = serviceRepository.getScheduled(columns);
        String[] c = columns.split(",");
        model.addAttribute("columns", c);
        model.addAttribute("scheduledServices", scheduledServices);
        return "scheduled-services";
    }

    @GetMapping("/schedule-service")
    public String showScheduleService() {
        return "schedule-service";
    }

    @PostMapping("/schedule-service")
    public String scheduleService(ScheduleService scheduleService, Authentication authentication) throws SQLException {
        if (scheduleService.getDate() == null || scheduleService.getDate().trim().isEmpty()
                || scheduleService.getCarModel() == null || scheduleService.getCarModel().trim().isEmpty()
                || scheduleService.getDescription() == null
                || scheduleService.getDescription().trim().isEmpty()
                || scheduleService.getDescription().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date, car model and a valid service description are required");
        }
        scheduleService.setCarModel(scheduleService.getCarModel().trim());
        scheduleService.setDescription(scheduleService.getDescription().trim());
        User user = (User) authentication.getPrincipal();
        serviceRepository.insertScheduledService(user.getId(), scheduleService);
        return "redirect:/scheduled-services";
    }

    @GetMapping("/services/{id}")
    public String showService(@PathVariable int id,
                              @RequestParam(required = false) Integer estimatedDurationMinutes,
                              Model model) {
        Service service = serviceWorkflowService.get(id);
        model.addAttribute("service", service);
        if (estimatedDurationMinutes != null) {
            model.addAttribute("estimatedDurationMinutes", estimatedDurationMinutes);
            model.addAttribute("availableTechnicians",
                    serviceWorkflowService.findAvailableTechnicians(id, estimatedDurationMinutes));
        }
        return "service-details";
    }

    @PostMapping("/services/{id}/assign")
    public String assignTechnician(@PathVariable int id,
                                   @RequestParam String technician,
                                   @RequestParam int estimatedDurationMinutes) {
        serviceWorkflowService.assignTechnician(id, technician, estimatedDurationMinutes);
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

    @GetMapping("/confirm-service-1")
    public String confirmService(HttpSession session, @RequestParam Integer id) {
        ServiceTicket serviceTicket = new ServiceTicket();
        serviceTicket.setTicketNumber(UUID.randomUUID());
        serviceTicket.setId(id);
        session.setAttribute("SERVICE_TICKET", serviceTicket);
        return "confirm-service-1";
    }

    @PostMapping("/confirm-service-2")
    public String confirmService2(HttpSession session, String time, Model model) {
        ServiceTicket serviceTicket = (ServiceTicket) session.getAttribute("SERVICE_TICKET");
        if (serviceTicket == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ticket has not been created.");
        }

        serviceTicket.setTime(time);

        model.addAttribute("serviceTicketNumber", serviceTicket.getTicketNumber());
        model.addAttribute("time", serviceTicket.getTime());
        return "confirm-service-2";
    }

    @PostMapping("/confirm-service-3")
    public String confirmService3(HttpSession session) throws SQLException {
        ServiceTicket serviceTicket = (ServiceTicket) session.getAttribute("SERVICE_TICKET");
        if (serviceTicket == null || serviceTicket.getTime() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ticket does not have time defined.");
        }

        serviceRepository.updateScheduledService(serviceTicket);
        session.removeAttribute("SERVICE_TICKET");

        return "redirect:/scheduled-services";
    }
}
