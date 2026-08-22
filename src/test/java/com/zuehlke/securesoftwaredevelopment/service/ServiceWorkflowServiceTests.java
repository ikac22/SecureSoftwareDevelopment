package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceWorkflowServiceTests {
    private ServiceRepository serviceRepository;
    private TechnicianDirectory technicianDirectory;
    private ServiceWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        serviceRepository = mock(ServiceRepository.class);
        technicianDirectory = mock(TechnicianDirectory.class);
        workflowService = new ServiceWorkflowService(serviceRepository, technicianDirectory);
    }

    @Test
    void overlappingAssignmentIsFilteredButAdjacentAssignmentIsAvailable() {
        Service candidate = service(1, LocalTime.of(10, 0), 60, ServiceStatus.SCHEDULED, null);
        Technician marko = new Technician("marko", "Marko", "marko@securecar.test");
        Technician ana = new Technician("ana", "Ana", "ana@securecar.test");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(candidate));
        when(technicianDirectory.findAll()).thenReturn(Arrays.asList(marko, ana));
        when(serviceRepository.findActiveAssignments("marko", 1)).thenReturn(Collections.singletonList(
                service(2, LocalTime.of(10, 30), 60, ServiceStatus.ASSIGNED, "marko")));
        when(serviceRepository.findActiveAssignments("ana", 1)).thenReturn(Collections.singletonList(
                service(3, LocalTime.of(11, 0), 60, ServiceStatus.IN_PROGRESS, "ana")));

        List<Technician> available = workflowService.findAvailableTechnicians(1, 60);

        assertThat(available).extracting(Technician::getId).containsExactly("ana");
    }

    @ParameterizedTest
    @CsvSource({
            "09:00, 120",
            "09:30, 60",
            "10:15, 15",
            "10:00, 60",
            "10:30, 60"
    })
    void everyOverlapShapeBlocksTechnician(String existingStart, int existingDuration) {
        Service candidate = service(1, LocalTime.of(10, 0), 60, ServiceStatus.SCHEDULED, null);
        Technician marko = new Technician("marko", "Marko", "marko@securecar.test");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(candidate));
        when(technicianDirectory.findAll()).thenReturn(Collections.singletonList(marko));
        when(serviceRepository.findActiveAssignments("marko", 1)).thenReturn(Collections.singletonList(
                service(2, LocalTime.parse(existingStart), existingDuration,
                        ServiceStatus.ASSIGNED, "marko")));

        assertThat(workflowService.findAvailableTechnicians(1, 60)).isEmpty();
    }

    @Test
    void assignmentRechecksAvailabilityBeforeUpdatingService() {
        Service candidate = service(1, LocalTime.of(10, 0), 60, ServiceStatus.SCHEDULED, null);
        Technician marko = new Technician("marko", "Marko", "marko@securecar.test");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(candidate));
        when(technicianDirectory.findAll()).thenReturn(Collections.singletonList(marko));
        when(serviceRepository.findActiveAssignments("marko", 1)).thenReturn(Collections.singletonList(
                service(2, LocalTime.of(9, 30), 60, ServiceStatus.ASSIGNED, "marko")));

        assertThatThrownBy(() -> workflowService.assignTechnician(1, "marko", 60))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(serviceRepository, never()).assignTechnician(anyInt(), anyString(), anyInt());
    }

    @Test
    void assignmentRequiresConfirmedTimeAndPositiveDuration() {
        Service withoutTime = new Service(1, 1, LocalDate.of(2030, 6, 1), null,
                "Honda", "Maintenance", null, ServiceStatus.SCHEDULED,
                null, null, null, null);
        when(serviceRepository.findById(1)).thenReturn(Optional.of(withoutTime));

        assertThatThrownBy(() -> workflowService.findAvailableTechnicians(1, 60))
                .isInstanceOf(ResponseStatusException.class);

        Service scheduled = service(1, LocalTime.of(10, 0), null, ServiceStatus.SCHEDULED, null);
        when(serviceRepository.findById(1)).thenReturn(Optional.of(scheduled));
        assertThatThrownBy(() -> workflowService.findAvailableTechnicians(1, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void invalidTransitionIsReportedAsConflict() {
        when(serviceRepository.start(1)).thenReturn(false);

        assertThatThrownBy(() -> workflowService.start(1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private Service service(int id, LocalTime time, Integer duration,
                            ServiceStatus status, String technician) {
        return new Service(id, 1, LocalDate.of(2030, 6, 1), time,
                "Honda", "Maintenance", "ticket", status, technician,
                duration, (LocalDateTime) null, null);
    }
}
