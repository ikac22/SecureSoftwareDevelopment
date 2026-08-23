package com.zuehlke.securesoftwaredevelopment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid}")
@AutoConfigureMockMvc
class LdapTechnicianSearchIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void regularSearchFiltersTechniciansBeforeAvailabilityCalculation() throws Exception {
        mockMvc.perform(get("/services/1/available-slots")
                .with(user("service-manager"))
                .param("date", "2030-06-01")
                .param("estimatedDurationMinutes", "60")
                .param("search", "Jovanovic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].technician.id").value("jelena.jovanovic"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void ldapWildcardInjectionExpandsDirectorySearch() throws Exception {
        mockMvc.perform(get("/services/1/available-slots")
                .with(user("service-manager"))
                .param("date", "2030-06-01")
                .param("estimatedDurationMinutes", "60")
                .param("search", "no-such-technician"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/services/1/available-slots")
                .with(user("service-manager"))
                .param("date", "2030-06-01")
                .param("estimatedDurationMinutes", "60")
                .param("search", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].technician.id").exists())
                .andExpect(jsonPath("$[12].technician.id").exists());
    }

    @Test
    void assignmentDoesNotTreatSearchMetacharactersAsTechnicianIdentity() throws Exception {
        mockMvc.perform(post("/services/1/assign")
                .with(user("service-manager"))
                .param("technician", "*")
                .param("date", "2030-06-01")
                .param("time", "10:00")
                .param("estimatedDurationMinutes", "60"))
                .andExpect(status().isBadRequest());
    }
}
