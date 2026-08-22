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

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(ServiceRepository.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ServiceRepositoryTests {

    @Autowired
    private ServiceRepository serviceRepository;

    @Test
    void seededServiceUsesScheduledStatusAndDescription() {
        Service service = serviceRepository.findById(1).orElseThrow(AssertionError::new);

        assertThat(service.getServiceStatus()).isEqualTo(ServiceStatus.SCHEDULED);
        assertThat(service.getDescription()).isEqualTo("Regular maintenance");
    }

    @Test
    void scheduledServiceStoresRequiredDescription() throws SQLException {
        ScheduleService request = new ScheduleService();
        request.setDate("2030-06-01");
        request.setCarModel("Honda Civic");
        request.setDescription("Replace brake pads");

        serviceRepository.insertScheduledService(2, request);

        assertThat(serviceRepository.getScheduled("description"))
                .anySatisfy(service -> assertThat(service.getProperties())
                        .containsExactly("Replace brake pads"));
    }

    @Test
    void guardedTransitionsStoreCompletionTime() {
        assertThat(serviceRepository.start(1)).isFalse();
        assertThat(serviceRepository.assignTechnician(1, "marko.markovic", 90)).isTrue();
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
        assertThat(serviceRepository.assignTechnician(1, "ana.anic", 45)).isTrue();
        assertThat(serviceRepository.cancel(1)).isTrue();

        Service canceled = serviceRepository.findById(1).orElseThrow(AssertionError::new);
        assertThat(canceled.getServiceStatus()).isEqualTo(ServiceStatus.CANCELED);
    }

    @Test
    void completedServiceDoesNotRemainAnActiveAssignment() {
        assertThat(serviceRepository.assignTechnician(1, "marko.markovic", 90)).isTrue();
        assertThat(serviceRepository.start(1)).isTrue();
        assertThat(serviceRepository.cancel(1)).isFalse();
        assertThat(serviceRepository.complete(1)).isTrue();

        assertThat(serviceRepository.findActiveAssignments("marko.markovic", -1)).isEmpty();
    }
}
