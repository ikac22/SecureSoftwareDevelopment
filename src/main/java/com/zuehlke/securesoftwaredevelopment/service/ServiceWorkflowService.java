package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class ServiceWorkflowService {
    private final ServiceRepository serviceRepository;
    private final TechnicianDirectory technicianDirectory;

    public ServiceWorkflowService(ServiceRepository serviceRepository, TechnicianDirectory technicianDirectory) {
        this.serviceRepository = serviceRepository;
        this.technicianDirectory = technicianDirectory;
    }

    public Service get(int serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    public List<Technician> findAvailableTechnicians(int serviceId, int estimatedDurationMinutes) {
        Service service = get(serviceId);
        validateAssignmentInput(service, estimatedDurationMinutes);
        return technicianDirectory.findAll().stream()
                .filter(technician -> isAvailable(
                        technician.getId(), service, estimatedDurationMinutes))
                .collect(Collectors.toList());
    }

    public synchronized void assignTechnician(int serviceId, String technicianId,
                                              int estimatedDurationMinutes) {
        Service service = get(serviceId);
        validateAssignmentInput(service, estimatedDurationMinutes);

        boolean knownTechnician = technicianDirectory.findAll().stream()
                .anyMatch(technician -> technician.getId().equals(technicianId));
        if (!knownTechnician) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown technician");
        }
        if (!isAvailable(technicianId, service, estimatedDurationMinutes)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Technician is no longer available for the selected time slot");
        }
        if (!serviceRepository.assignTechnician(serviceId, technicianId, estimatedDurationMinutes)) {
            throw invalidTransition();
        }
    }

    public void cancel(int serviceId) {
        if (!serviceRepository.cancel(serviceId)) {
            throw invalidTransition();
        }
    }

    public void start(int serviceId) {
        if (!serviceRepository.start(serviceId)) {
            throw invalidTransition();
        }
    }

    public void complete(int serviceId) {
        if (!serviceRepository.complete(serviceId)) {
            throw invalidTransition();
        }
    }

    private void validateAssignmentInput(Service service, int estimatedDurationMinutes) {
        if (service.getServiceStatus() != ServiceStatus.SCHEDULED
                && service.getServiceStatus() != ServiceStatus.ASSIGNED) {
            throw invalidTransition();
        }
        if (service.getDate() == null || service.getTime() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Service time must be confirmed before assigning a technician");
        }
        if (estimatedDurationMinutes <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estimated duration must be positive");
        }
    }

    private boolean isAvailable(String technicianId, Service candidate,
                                int estimatedDurationMinutes) {
        LocalDateTime candidateStart = LocalDateTime.of(candidate.getDate(), candidate.getTime());
        LocalDateTime candidateEnd = candidateStart.plusMinutes(estimatedDurationMinutes);

        return serviceRepository.findActiveAssignments(technicianId, candidate.getId()).stream()
                .noneMatch(existing -> overlaps(candidateStart, candidateEnd, existing));
    }

    private boolean overlaps(LocalDateTime candidateStart, LocalDateTime candidateEnd,
                             Service existing) {
        if (existing.getDate() == null || existing.getTime() == null
                || existing.getEstimatedDurationMinutes() == null) {
            return false;
        }
        LocalDateTime existingStart = LocalDateTime.of(existing.getDate(), existing.getTime());
        LocalDateTime existingEnd = existingStart.plusMinutes(existing.getEstimatedDurationMinutes());
        return existingStart.isBefore(candidateEnd) && existingEnd.isAfter(candidateStart);
    }

    private ResponseStatusException invalidTransition() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Invalid service status transition");
    }
}
