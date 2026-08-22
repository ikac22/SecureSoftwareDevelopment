package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.ScheduleService;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.domain.TechnicianAvailability;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceControllerTests {
    private ServiceRepository serviceRepository;
    private ServiceWorkflowService workflowService;
    private ServiceController controller;

    @BeforeEach
    void setUp() {
        serviceRepository = mock(ServiceRepository.class);
        workflowService = mock(ServiceWorkflowService.class);
        controller = new ServiceController(serviceRepository, workflowService);
    }

    @Test
    void schedulingRejectsBlankDescription() {
        ScheduleService request = new ScheduleService();
        request.setCarModel("Honda");
        request.setDescription("   ");

        assertThatThrownBy(() -> controller.scheduleService(request, mock(Authentication.class)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void detailsPageLoadsServiceWithoutRunningAvailabilitySearch() {
        Service service = service(ServiceStatus.SCHEDULED);
        when(workflowService.get(1)).thenReturn(service);
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.showService(1, model)).isEqualTo("service-details");
        assertThat(model.get("service")).isEqualTo(service);
    }

    @Test
    void availabilityEndpointParsesDateAndDelegatesToWorkflow() {
        Technician technician = new Technician("ana", "Ana", "ana@securecar.test");
        List<TechnicianAvailability> expected = Collections.singletonList(
                new TechnicianAvailability(technician, Collections.singletonList("10:30")));
        when(workflowService.findAvailableSlots(1, LocalDate.of(2030, 6, 1), 90))
                .thenReturn(expected);

        assertThat(controller.availableSlots(1, "2030-06-01", 90)).isSameAs(expected);
    }

    @Test
    void availabilityEndpointRejectsInvalidDate() {
        assertThatThrownBy(() -> controller.availableSlots(1, "not-a-date", 60))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void workflowActionsDelegateAndRedirectToDetails() {
        assertThat(controller.assignTechnician(
                1, "ana", "2030-06-01", "13:30", 90)).isEqualTo("redirect:/services/1");
        assertThat(controller.cancelService(1)).isEqualTo("redirect:/services/1");
        assertThat(controller.startService(1)).isEqualTo("redirect:/services/1");
        assertThat(controller.completeService(1)).isEqualTo("redirect:/services/1");

        verify(workflowService).assignTechnician(
                1, "ana", LocalDate.of(2030, 6, 1), LocalTime.of(13, 30), 90);
        verify(workflowService).cancel(1);
        verify(workflowService).start(1);
        verify(workflowService).complete(1);
    }

    private Service service(ServiceStatus status) {
        return new Service(1, 1, null, null, "Honda", "Maintenance",
                status, null, null, null, null);
    }
}
