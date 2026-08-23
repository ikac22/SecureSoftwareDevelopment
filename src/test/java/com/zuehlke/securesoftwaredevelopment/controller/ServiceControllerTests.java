package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.ScheduleService;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.domain.TechnicianAvailability;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkflowService;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceControllerTests {
    private ServiceRepository serviceRepository;
    private ServiceWorkflowService workflowService;
    private ServiceWorkService serviceWorkService;
    private ServiceController controller;

    @BeforeEach
    void setUp() {
        serviceRepository = mock(ServiceRepository.class);
        workflowService = mock(ServiceWorkflowService.class);
        serviceWorkService = mock(ServiceWorkService.class);
        controller = new ServiceController(serviceRepository, workflowService, serviceWorkService);
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
    void servicesPageLoadsOnlyLoggedInCustomersServices() {
        User customer = new User(42, "customer", "password");
        Authentication authentication = mock(Authentication.class);
        Service service = service(ServiceStatus.SCHEDULED);
        when(authentication.getPrincipal()).thenReturn(customer);
        when(serviceRepository.findByPersonId(42)).thenReturn(Collections.singletonList(service));
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.showServices(authentication, model)).isEqualTo("scheduled-services");
        assertThat(model.get("scheduledServices")).isEqualTo(Collections.singletonList(service));
        assertThat(model.containsAttribute("columns")).isFalse();
        verify(serviceRepository).findByPersonId(42);
    }

    @Test
    void servicesPageRequiresCustomerPrincipal() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("ldap-service-staff");

        assertThatThrownBy(() -> controller.showServices(authentication, new ConcurrentModel()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void detailsPageLoadsServiceWithoutRunningAvailabilitySearch() {
        Service service = service(ServiceStatus.SCHEDULED);
        when(workflowService.get(1)).thenReturn(service);
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.showService(1, model)).isEqualTo("service-details");
        assertThat(model.get("service")).isEqualTo(service);
        verify(serviceWorkService, never()).getForDisplay(1);
    }

    @Test
    void detailsPageLoadsEditableWorkForInProgressService() {
        Service service = service(ServiceStatus.IN_PROGRESS);
        ServiceDetails details = new ServiceDetails(1);
        when(workflowService.get(1)).thenReturn(service);
        when(serviceWorkService.getForDisplay(1)).thenReturn(details);
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.showService(1, model)).isEqualTo("service-details");
        assertThat(model.get("serviceDetails")).isSameAs(details);
        verify(serviceWorkService).getForDisplay(1);
    }

    @Test
    void detailsPageLoadsReadOnlyWorkForCompletedService() {
        Service service = service(ServiceStatus.COMPLETED);
        ServiceDetails details = new ServiceDetails(1);
        when(workflowService.get(1)).thenReturn(service);
        when(serviceWorkService.getForDisplay(1)).thenReturn(details);
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.showService(1, model)).isEqualTo("service-details");
        assertThat(model.get("serviceDetails")).isSameAs(details);
        verify(serviceWorkService).getForDisplay(1);
    }

    @Test
    void availabilityEndpointParsesDateAndDelegatesToWorkflow() {
        Technician technician = new Technician("ana", "Ana", "ana@securecar.test");
        List<TechnicianAvailability> expected = Collections.singletonList(
                new TechnicianAvailability(technician, Collections.singletonList("10:30")));
        when(workflowService.findAvailableSlots(1, LocalDate.of(2030, 6, 1), 50))
                .thenReturn(expected);

        assertThat(controller.availableSlots(1, "2030-06-01", 50)).isSameAs(expected);
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
                1, "ana", "2030-06-01", "13:30", 50)).isEqualTo("redirect:/services/1");
        assertThat(controller.cancelService(1)).isEqualTo("redirect:/services/1");
        assertThat(controller.startService(1)).isEqualTo("redirect:/services/1");
        assertThat(controller.completeService(1)).isEqualTo("redirect:/services/1");

        verify(workflowService).assignTechnician(
                1, "ana", LocalDate.of(2030, 6, 1), LocalTime.of(13, 30), 50);
        verify(workflowService).cancel(1);
        verify(workflowService).start(1);
        verify(workflowService).complete(1);
    }

    private Service service(ServiceStatus status) {
        return new Service(1, 1, null, null, "Honda", "Maintenance",
                status, null, null, null, null);
    }
}
