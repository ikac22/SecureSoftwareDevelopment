package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.domain.TechnicianAvailability;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceWorkflowServiceTests {
    private static final LocalDate SERVICE_DATE = LocalDate.of(2030, 6, 1);

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
    void availabilityUsesThirtyMinuteSlotsWithinBusinessHoursAndFiltersOverlaps() {
        Technician marko = technician("marko", "Marko");
        Technician ana = technician("ana", "Ana");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(
                service(1, null, null, ServiceStatus.SCHEDULED, null)));
        when(technicianDirectory.findAll()).thenReturn(Arrays.asList(marko, ana));
        when(serviceRepository.findActiveAssignments("marko", 1)).thenReturn(Collections.singletonList(
                service(2, LocalTime.of(9, 0), 60, ServiceStatus.ASSIGNED, "marko")));
        when(serviceRepository.findActiveAssignments("ana", 1)).thenReturn(Collections.emptyList());

        List<TechnicianAvailability> result =
                workflowService.findAvailableSlots(1, SERVICE_DATE, 60);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAvailableStartTimes())
                .startsWith("08:00")
                .doesNotContain("08:30", "09:00", "09:30")
                .contains("10:00")
                .endsWith("16:00");
        assertThat(result.get(1).getAvailableStartTimes())
                .hasSize(17)
                .startsWith("08:00", "08:30")
                .endsWith("15:30", "16:00");
    }

    @Test
    void longerDurationMovesLastAvailableStartEarlier() {
        Technician marko = technician("marko", "Marko");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(
                service(1, null, null, ServiceStatus.SCHEDULED, null)));
        when(technicianDirectory.findAll()).thenReturn(Collections.singletonList(marko));
        when(serviceRepository.findActiveAssignments("marko", 1)).thenReturn(Collections.emptyList());

        List<String> slots = workflowService.findAvailableSlots(1, SERVICE_DATE, 90)
                .get(0).getAvailableStartTimes();

        assertThat(slots).startsWith("08:00").endsWith("15:30").hasSize(16);
    }

    @ParameterizedTest
    @CsvSource({
            "09:00, 120",
            "09:30, 60",
            "10:15, 15",
            "10:00, 60",
            "10:30, 60"
    })
    void everyOverlapShapeBlocksTheAffectedSlot(String existingStart, int existingDuration) {
        Technician marko = technician("marko", "Marko");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(
                service(1, null, null, ServiceStatus.SCHEDULED, null)));
        when(technicianDirectory.findAll()).thenReturn(Collections.singletonList(marko));
        when(serviceRepository.findActiveAssignments("marko", 1)).thenReturn(Collections.singletonList(
                service(2, LocalTime.parse(existingStart), existingDuration,
                        ServiceStatus.ASSIGNED, "marko")));

        assertThat(workflowService.findAvailableSlots(1, SERVICE_DATE, 60)
                .get(0).getAvailableStartTimes()).doesNotContain("10:00");
    }

    @Test
    void assignmentRechecksAvailabilityBeforeUpdatingService() {
        Technician marko = technician("marko", "Marko");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(
                service(1, null, null, ServiceStatus.SCHEDULED, null)));
        when(technicianDirectory.findAll()).thenReturn(Collections.singletonList(marko));
        when(serviceRepository.findActiveAssignments("marko", 1)).thenReturn(Collections.singletonList(
                service(2, LocalTime.of(9, 30), 60, ServiceStatus.ASSIGNED, "marko")));

        assertThatThrownBy(() -> workflowService.assignTechnician(
                1, "marko", SERVICE_DATE, LocalTime.of(10, 0), 60))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(serviceRepository, never()).assignTechnician(
                anyInt(), anyString(), any(LocalDate.class), any(LocalTime.class), anyInt());
    }

    @Test
    void assignmentPersistsTheCompleteSelection() {
        Technician ana = technician("ana", "Ana");
        when(serviceRepository.findById(1)).thenReturn(Optional.of(
                service(1, null, null, ServiceStatus.SCHEDULED, null)));
        when(technicianDirectory.findAll()).thenReturn(Collections.singletonList(ana));
        when(serviceRepository.findActiveAssignments("ana", 1)).thenReturn(Collections.emptyList());
        when(serviceRepository.assignTechnician(
                1, "ana", SERVICE_DATE, LocalTime.of(13, 30), 90)).thenReturn(true);

        workflowService.assignTechnician(1, "ana", SERVICE_DATE, LocalTime.of(13, 30), 90);

        verify(serviceRepository).assignTechnician(
                1, "ana", SERVICE_DATE, LocalTime.of(13, 30), 90);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 15, 45, 570})
    void durationMustFitTheBusinessDayInThirtyMinuteIncrements(int duration) {
        when(serviceRepository.findById(1)).thenReturn(Optional.of(
                service(1, null, null, ServiceStatus.SCHEDULED, null)));

        assertThatThrownBy(() -> workflowService.findAvailableSlots(1, SERVICE_DATE, duration))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @CsvSource({"07:30, 60", "08:15, 60", "16:30, 60", "17:00, 30"})
    void assignmentRejectsStartsOutsideGeneratedSlots(String time, int duration) {
        when(serviceRepository.findById(1)).thenReturn(Optional.of(
                service(1, null, null, ServiceStatus.SCHEDULED, null)));

        assertThatThrownBy(() -> workflowService.assignTechnician(
                1, "marko", SERVICE_DATE, LocalTime.parse(time), duration))
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

    private Technician technician(String id, String displayName) {
        return new Technician(id, displayName, id + "@securecar.test");
    }

    private Service service(int id, LocalTime time, Integer duration,
                            ServiceStatus status, String technician) {
        return new Service(id, 1, time == null ? null : SERVICE_DATE, time,
                "Honda", "Maintenance", status, technician,
                duration, (LocalDateTime) null, null);
    }
}
