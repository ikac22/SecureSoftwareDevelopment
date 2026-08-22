package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.ScheduleService;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(ServiceRepository.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ServiceRepositoryTests {
    private static final LocalDate SERVICE_DATE = LocalDate.of(2030, 6, 1);
    private static final LocalTime SERVICE_TIME = LocalTime.of(10, 30);

    @Autowired
    private ServiceRepository serviceRepository;

    @Test
    void seededServiceStartsScheduledWithoutAnAssignedDate() {
        Service service = serviceRepository.findById(1).orElseThrow(AssertionError::new);

        assertThat(service.getServiceStatus()).isEqualTo(ServiceStatus.SCHEDULED);
        assertThat(service.getDescription()).isEqualTo("Regular maintenance");
        assertThat(service.getDate()).isNull();
        assertThat(service.getTime()).isNull();
    }

    @Test
    void scheduledServiceStoresRequiredDescriptionWithoutAssignmentData() throws SQLException {
        ScheduleService request = new ScheduleService();
        request.setCarModel("Honda Civic");
        request.setDescription("Replace brake pads");

        serviceRepository.insertScheduledService(2, request);

        assertThat(serviceRepository.getScheduled("description,date,time"))
                .anySatisfy(service -> assertThat(service.getProperties())
                        .containsExactly("Replace brake pads", null, null));
    }

    @Test
    void assignmentStoresDateTimeDurationAndTechnicianTogether() {
        assertThat(serviceRepository.assignTechnician(
                1, "marko.markovic", SERVICE_DATE, SERVICE_TIME, 90)).isTrue();

        Service assigned = serviceRepository.findById(1).orElseThrow(AssertionError::new);
        assertThat(assigned.getServiceStatus()).isEqualTo(ServiceStatus.ASSIGNED);
        assertThat(assigned.getTechnician()).isEqualTo("marko.markovic");
        assertThat(assigned.getDate()).isEqualTo(SERVICE_DATE);
        assertThat(assigned.getTime()).isEqualTo(SERVICE_TIME);
        assertThat(assigned.getEstimatedDurationMinutes()).isEqualTo(90);
    }

    @Test
    void guardedTransitionsStoreCompletionTime() {
        assertThat(serviceRepository.start(1)).isFalse();
        assertThat(serviceRepository.assignTechnician(
                1, "marko.markovic", SERVICE_DATE, SERVICE_TIME, 90)).isTrue();
        assertThat(serviceRepository.start(1)).isTrue();
        assertThat(serviceRepository.start(1)).isFalse();
        assertThat(serviceRepository.complete(1)).isTrue();
        assertThat(serviceRepository.complete(1)).isFalse();

        Service completed = serviceRepository.findById(1).orElseThrow(AssertionError::new);
        assertThat(completed.getServiceStatus()).isEqualTo(ServiceStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void cancellationIsAllowedOnlyBeforeWorkStarts() {
        assertThat(serviceRepository.cancel(1)).isTrue();
        assertThat(serviceRepository.cancel(1)).isFalse();

        Service canceled = serviceRepository.findById(1).orElseThrow(AssertionError::new);
        assertThat(canceled.getServiceStatus()).isEqualTo(ServiceStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isNotNull();
    }

    @Test
    void assignedServiceCanBeCanceledButStartedServiceCannot() {
        assertThat(serviceRepository.assignTechnician(
                1, "ana.anic", SERVICE_DATE, SERVICE_TIME, 60)).isTrue();
        assertThat(serviceRepository.cancel(1)).isTrue();

        Service canceled = serviceRepository.findById(1).orElseThrow(AssertionError::new);
        assertThat(canceled.getServiceStatus()).isEqualTo(ServiceStatus.CANCELED);
    }

    @Test
    void completedServiceDoesNotRemainAnActiveAssignment() {
        assertThat(serviceRepository.assignTechnician(
                1, "marko.markovic", SERVICE_DATE, SERVICE_TIME, 90)).isTrue();
        assertThat(serviceRepository.start(1)).isTrue();
        assertThat(serviceRepository.cancel(1)).isFalse();
        assertThat(serviceRepository.complete(1)).isTrue();

        assertThat(serviceRepository.findActiveAssignments("marko.markovic", -1)).isEmpty();
    }
}
