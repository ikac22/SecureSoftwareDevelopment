package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.service.ServiceWorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceWorkControllerTests {
    private ServiceWorkService serviceWorkService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        serviceWorkService = mock(ServiceWorkService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ServiceWorkController(serviceWorkService)).build();
    }

    @Test
    void partSearchCarriesNestedOperatorFromHttpJsonToApplicationService() throws Exception {
        when(serviceWorkService.searchParts(anyMap())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/catalog/parts/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filters\":{\"name\":{\"$ne\":null}}}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(serviceWorkService).searchParts(captor.capture());
        assertThat(captor.getValue().get("name")).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) captor.getValue().get("name")).containsKey("$ne");
    }

    @Test
    void addServiceEndpointPassesCatalogIdAndExpectedVersion() throws Exception {
        ServiceDetails details = new ServiceDetails(7);
        when(serviceWorkService.addServiceFromCatalog(7, "brakes", 4L)).thenReturn(details);

        mockMvc.perform(post("/api/services/7/work/services/from-catalog")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogId\":\"brakes\",\"version\":4}"))
                .andExpect(status().isOk());

        verify(serviceWorkService).addServiceFromCatalog(7, "brakes", 4L);
    }
}
