package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.ScheduleService;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.Technician;
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
        request.setDescription("   ");

        assertThatThrownBy(() -> controller.scheduleService(request, mock(Authentication.class)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void detailsPageShowsAvailableTechniciansForEnteredDuration() {
        Service service = new Service(1, 1, LocalDate.of(2030, 6, 1), LocalTime.of(10, 0),
                "Honda", "Maintenance", "ticket", ServiceStatus.SCHEDULED,
                null, null, null, null);
        Technician technician = new Technician("ana", "Ana", "ana@securecar.test");
        when(workflowService.get(1)).thenReturn(service);
        when(workflowService.findAvailableTechnicians(1, 60))
                .thenReturn(Collections.singletonList(technician));
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.showService(1, 60, model)).isEqualTo("service-details");
        assertThat(model.get("service")).isEqualTo(service);
        assertThat(model.get("availableTechnicians"))
                .isEqualTo(Collections.singletonList(technician));
    }

    @Test
    void workflowActionsDelegateAndRedirectToDetails() {
        assertThat(controller.assignTechnician(1, "ana", 60)).isEqualTo("redirect:/services/1");
        assertThat(controller.cancelService(1)).isEqualTo("redirect:/services/1");
        assertThat(controller.startService(1)).isEqualTo("redirect:/services/1");
        assertThat(controller.completeService(1)).isEqualTo("redirect:/services/1");

        verify(workflowService).assignTechnician(1, "ana", 60);
        verify(workflowService).cancel(1);
        verify(workflowService).start(1);
        verify(workflowService).complete(1);
    }
}
