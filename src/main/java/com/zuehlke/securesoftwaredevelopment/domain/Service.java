package com.zuehlke.securesoftwaredevelopment.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class Service {
    private final Integer id;
    private final Integer personId;
    private final List<String> properties;
    private final LocalDate date;
    private final LocalTime time;
    private final String carModel;
    private final String description;
    private final String ticketNumber;
    private final ServiceStatus serviceStatus;
    private final String technician;
    private final Integer estimatedDurationMinutes;
    private final LocalDateTime completedAt;
    private final LocalDateTime canceledAt;

    public Service(Integer id, Integer personId, List<String> properties) {
        this.id = id;
        this.personId = personId;
        this.properties = properties;
        this.date = null;
        this.time = null;
        this.carModel = null;
        this.description = null;
        this.ticketNumber = null;
        this.serviceStatus = null;
        this.technician = null;
        this.estimatedDurationMinutes = null;
        this.completedAt = null;
        this.canceledAt = null;
    }

    public Service(Integer id, Integer personId, LocalDate date, LocalTime time,
                   String carModel, String description, String ticketNumber,
                   ServiceStatus serviceStatus, String technician,
                   Integer estimatedDurationMinutes, LocalDateTime completedAt,
                   LocalDateTime canceledAt) {
        this.id = id;
        this.personId = personId;
        this.properties = Collections.emptyList();
        this.date = date;
        this.time = time;
        this.carModel = carModel;
        this.description = description;
        this.ticketNumber = ticketNumber;
        this.serviceStatus = serviceStatus;
        this.technician = technician;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.completedAt = completedAt;
        this.canceledAt = canceledAt;
    }

    public Integer getId() {
        return id;
    }

    public List<String> getProperties() {
        return properties;
    }

    public Integer getPersonId() {
        return personId;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public String getCarModel() {
        return carModel;
    }

    public String getDescription() {
        return description;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public ServiceStatus getServiceStatus() {
        return serviceStatus;
    }

    public String getTechnician() {
        return technician;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public boolean isTerminal() {
        return serviceStatus == ServiceStatus.COMPLETED || serviceStatus == ServiceStatus.CANCELED;
    }
}
