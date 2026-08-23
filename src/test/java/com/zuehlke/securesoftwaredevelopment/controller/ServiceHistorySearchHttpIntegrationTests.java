package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:${random.uuid}",
        "spring.data.mongodb.database=secure-software-development-history-test"
})
class ServiceHistorySearchHttpIntegrationTests {
    @Autowired
    private ServiceHistoryController serviceHistoryController;

    private MockMvc mockMvc;
    private Authentication bruce;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serviceHistoryController).build();
        User customer = new User(1, "bruce", "wayne");
        bruce = new UsernamePasswordAuthenticationToken(
                customer, customer.getPassword(), Collections.emptyList());
    }

    @Test
    void normalHistorySearchReturnsOnlyAuthenticatedCustomersCompletedServices() throws Exception {
        mockMvc.perform(post("/api/my/service-history/search")
                .principal(bruce)
                .contentType(APPLICATION_JSON)
                .content("{\"filters\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceId").value(3))
                .andExpect(jsonPath("$[0].customerId").value(1))
                .andExpect(jsonPath("$[0].carModel").value("Ford Focus"));
    }

    @Test
    void operatorInReservedCustomerFilterBypassesImplicitOwnershipPredicate() throws Exception {
        mockMvc.perform(post("/api/my/service-history/search")
                .principal(bruce)
                .contentType(APPLICATION_JSON)
                .content("{\"filters\":{\"customerId\":{\"$ne\":1}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceId").value(4))
                .andExpect(jsonPath("$[0].customerId").value(2))
                .andExpect(jsonPath("$[0].carModel").value("Volkswagen Golf"));
    }
}
