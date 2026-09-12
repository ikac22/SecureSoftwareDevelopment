package com.zuehlke.securesoftwaredevelopment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:${random.uuid}",
        "spring.data.mongodb.database=secure-software-development-cwe943-experiment"
})
class Cwe943ExperimentTests {
    private static final String ATTACK_REQUEST = "{\n"
            + "  \"filters\": {\"customerId\": {\"$ne\": 1}},\n"
            + "  \"view\": {\"customerId\": 1, \"pricingPolicy\": 1}\n"
            + "}";

    private static final String NORMAL_REQUEST = "{\n"
            + "  \"filters\": {\"carModel\": \"Ford Focus\"},\n"
            + "  \"view\": {\"performedServices.name\": 1, \"performedServices.usedParts\": 1}\n"
            + "}";

    @Autowired
    private ServiceHistoryController serviceHistoryController;

    private final ObjectMapper objectMapper = new ObjectMapper();
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
    void runCwe943Experiment() throws Exception {
        String phase = System.getProperty("cwe943.phase", "vulnerable");
        assertTrue("vulnerable".equals(phase) || "mitigated".equals(phase),
                "cwe943.phase must be vulnerable or mitigated");

        MvcResult attackResult = mockMvc.perform(post("/api/my/service-history/search")
                .principal(bruce)
                .contentType(APPLICATION_JSON)
                .content(ATTACK_REQUEST))
                .andReturn();

        String attackResponse = attackResult.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(attackResponse);
        boolean foreignServiceReturned = containsForeignCustomerService(response, 1);
        boolean customerIdExposed = containsField(response, "customerId");
        boolean pricingPolicyExposed = containsField(response, "pricingPolicy");

        writeEvidence(phase, ATTACK_REQUEST, attackResponse,
                attackResult.getResponse().getStatus(), foreignServiceReturned,
                customerIdExposed, pricingPolicyExposed);
        printEvidence(phase, ATTACK_REQUEST, attackResponse,
                attackResult.getResponse().getStatus(), foreignServiceReturned,
                customerIdExposed, pricingPolicyExposed);

        assertEquals(200, attackResult.getResponse().getStatus());
        if ("vulnerable".equals(phase)) {
            assertTrue(foreignServiceReturned, "attack must return another customer's completed service");
            assertTrue(customerIdExposed, "attack must expose customerId");
            assertTrue(pricingPolicyExposed, "attack must expose pricingPolicy");
        } else {
            assertFalse(foreignServiceReturned, "mitigation must preserve ownership boundary");
            assertFalse(pricingPolicyExposed, "mitigation must prevent arbitrary projection expansion");
            verifyNormalSearchStillWorks();
        }
    }

    private void verifyNormalSearchStillWorks() throws Exception {
        MvcResult normalResult = mockMvc.perform(post("/api/my/service-history/search")
                .principal(bruce)
                .contentType(APPLICATION_JSON)
                .content(NORMAL_REQUEST))
                .andReturn();

        JsonNode response = objectMapper.readTree(normalResult.getResponse().getContentAsString());
        assertEquals(200, normalResult.getResponse().getStatus());
        assertEquals(1, response.size());
        assertEquals(3, response.get(0).path("serviceId").asInt());
        assertEquals("Ford Focus", response.get(0).path("carModel").asText());
    }

    private boolean containsForeignCustomerService(JsonNode response, int authenticatedCustomerId) {
        if (!response.isArray()) {
            return false;
        }
        for (JsonNode item : response) {
            JsonNode customerId = item.get("customerId");
            if (customerId != null && customerId.isInt()
                    && customerId.asInt() != authenticatedCustomerId) {
                return true;
            }
        }
        return false;
    }

    private boolean containsField(JsonNode response, String fieldName) {
        if (!response.isArray()) {
            return false;
        }
        for (JsonNode item : response) {
            if (item.has(fieldName) && !item.get(fieldName).isNull()) {
                return true;
            }
        }
        return false;
    }

    private void writeEvidence(String phase,
                               String request,
                               String response,
                               int status,
                               boolean foreignServiceReturned,
                               boolean customerIdExposed,
                               boolean pricingPolicyExposed) throws Exception {
        Path evidenceDir = Paths.get("attacks", "cwe-943", "evidence", phase);
        Files.createDirectories(evidenceDir);
        Files.write(evidenceDir.resolve("request.json"), request.getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("response.json"), response.getBytes(StandardCharsets.UTF_8));

        String result = resultText(status, foreignServiceReturned, customerIdExposed, pricingPolicyExposed);
        Files.write(evidenceDir.resolve("result.txt"), result.getBytes(StandardCharsets.UTF_8));
    }

    private void printEvidence(String phase,
                               String request,
                               String response,
                               int status,
                               boolean foreignServiceReturned,
                               boolean customerIdExposed,
                               boolean pricingPolicyExposed) {
        System.out.println("CWE943_EVIDENCE_BEGIN phase=" + phase);
        System.out.println("CWE943_REQUEST_BEGIN");
        System.out.println(request);
        System.out.println("CWE943_REQUEST_END");
        System.out.println("CWE943_RESPONSE_BEGIN");
        System.out.println(response);
        System.out.println("CWE943_RESPONSE_END");
        System.out.print(resultText(status, foreignServiceReturned, customerIdExposed, pricingPolicyExposed));
        System.out.println("CWE943_EVIDENCE_END");
    }

    private String resultText(int status,
                              boolean foreignServiceReturned,
                              boolean customerIdExposed,
                              boolean pricingPolicyExposed) {
        return "httpStatus=" + status + "\n"
                + "foreignServiceReturned=" + foreignServiceReturned + "\n"
                + "customerIdExposed=" + customerIdExposed + "\n"
                + "pricingPolicyExposed=" + pricingPolicyExposed + "\n";
    }
}
