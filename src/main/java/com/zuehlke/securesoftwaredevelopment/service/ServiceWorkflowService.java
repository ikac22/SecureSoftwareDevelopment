package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.domain.TechnicianAvailability;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Service
public class ServiceWorkflowService {
    static final LocalTime OPENING_TIME = LocalTime.of(8, 0);
    static final LocalTime CLOSING_TIME = LocalTime.of(17, 0);
    static final int SLOT_MINUTES = 30;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

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

    public List<TechnicianAvailability> findAvailableSlots(int serviceId, LocalDate date,
                                                            int estimatedDurationMinutes) {
        Service service = get(serviceId);
        validateAssignableStatus(service);
        validateDate(date);
        validateDuration(estimatedDurationMinutes);
        int allocationMinutes = allocatedDurationMinutes(estimatedDurationMinutes);

        List<TechnicianAvailability> availability = new ArrayList<>();
        for (Technician technician : technicianDirectory.findAll()) {
            List<Service> activeAssignments =
                    serviceRepository.findActiveAssignments(technician.getId(), serviceId);
            List<String> availableStartTimes = new ArrayList<>();

            for (LocalTime start = OPENING_TIME;
                 !start.plusMinutes(allocationMinutes).isAfter(CLOSING_TIME);
                 start = start.plusMinutes(SLOT_MINUTES)) {
                if (isAvailable(date, start, allocationMinutes, activeAssignments)) {
                    availableStartTimes.add(start.format(TIME_FORMAT));
                }
            }
            availability.add(new TechnicianAvailability(technician, availableStartTimes));
        }
        return availability;
    }

    public synchronized void assignTechnician(int serviceId, String technicianId, LocalDate date,
                                              LocalTime time, int estimatedDurationMinutes) {
        Service service = get(serviceId);
        validateAssignableStatus(service);
        validateDate(date);
        validateDuration(estimatedDurationMinutes);
        int allocationMinutes = allocatedDurationMinutes(estimatedDurationMinutes);
        validateStartTime(time, allocationMinutes);

        boolean knownTechnician = technicianDirectory.findAll().stream()
                .anyMatch(technician -> technician.getId().equals(technicianId));
        if (!knownTechnician) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown technician");
        }

        List<Service> activeAssignments = serviceRepository.findActiveAssignments(technicianId, serviceId);
        if (!isAvailable(date, time, allocationMinutes, activeAssignments)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Technician is no longer available for the selected time slot");
        }
        if (!serviceRepository.assignTechnician(
                serviceId, technicianId, date, time, estimatedDurationMinutes)) {
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

    private void validateAssignableStatus(Service service) {
        if (service.getServiceStatus() != ServiceStatus.SCHEDULED
                && service.getServiceStatus() != ServiceStatus.ASSIGNED) {
            throw invalidTransition();
        }
    }

    private void validateDate(LocalDate date) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service date is required");
        }
    }

    private void validateDuration(int estimatedDurationMinutes) {
        int businessDayMinutes = (int) java.time.Duration.between(OPENING_TIME, CLOSING_TIME).toMinutes();
        if (estimatedDurationMinutes <= 0
                || estimatedDurationMinutes > businessDayMinutes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estimated duration must be positive and fit within business hours");
        }
    }

    static int allocatedDurationMinutes(int estimatedDurationMinutes) {
        return ((estimatedDurationMinutes + SLOT_MINUTES - 1) / SLOT_MINUTES) * SLOT_MINUTES;
    }

    private void validateStartTime(LocalTime time, int allocatedDurationMinutes) {
        if (time == null || time.getSecond() != 0 || time.getNano() != 0
                || time.getMinute() % SLOT_MINUTES != 0
                || time.isBefore(OPENING_TIME)
                || time.plusMinutes(allocatedDurationMinutes).isAfter(CLOSING_TIME)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start time must be a 30-minute slot between 08:00 and 17:00");
        }
    }

    private boolean isAvailable(LocalDate date, LocalTime time, int allocatedDurationMinutes,
                                List<Service> activeAssignments) {
        LocalDateTime candidateStart = LocalDateTime.of(date, time);
        LocalDateTime candidateEnd = candidateStart.plusMinutes(allocatedDurationMinutes);
        return activeAssignments.stream()
                .noneMatch(existing -> overlaps(candidateStart, candidateEnd, existing));
    }

    private boolean overlaps(LocalDateTime candidateStart, LocalDateTime candidateEnd,
                             Service existing) {
        if (existing.getDate() == null || existing.getTime() == null
                || existing.getEstimatedDurationMinutes() == null) {
            return false;
        }
        LocalDateTime existingStart = LocalDateTime.of(existing.getDate(), existing.getTime());
        LocalDateTime existingEnd = existingStart.plusMinutes(
                allocatedDurationMinutes(existing.getEstimatedDurationMinutes()));
        return existingStart.isBefore(candidateEnd) && existingEnd.isAfter(candidateStart);
    }

    private ResponseStatusException invalidTransition() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Invalid service status transition");
    }
}
