package com.zuehlke.securesoftwaredevelopment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PartCatalogSearchHttpIntegrationTests {
    @Autowired
    private ServiceWorkController serviceWorkController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serviceWorkController).build();
    }

    @Test
    void operatorPayloadExpandsResultsThroughHttpAndMongo() throws Exception {
        mockMvc.perform(post("/api/catalog/parts/search")
                .contentType(APPLICATION_JSON)
                .content("{\"filters\":{\"name\":\"Front brake pad set\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/catalog/parts/search")
                .contentType(APPLICATION_JSON)
                .content("{\"filters\":{\"name\":{\"$ne\":null}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }
}
