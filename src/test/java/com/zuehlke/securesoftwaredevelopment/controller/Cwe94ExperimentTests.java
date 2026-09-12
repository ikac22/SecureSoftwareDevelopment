package com.zuehlke.securesoftwaredevelopment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.service.ServicePricingPolicyEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class Cwe94ExperimentTests {
    private static final String BASELINE_PARTNER_CODE = "NONE";
    private static final String ATTACK_PARTNER_CODE = "x' == 'x' or 'x";
    private static final String VALID_PARTNER_CODE = "FLEET-10";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Authentication customer;

    @BeforeEach
    void setUp() {
        LoyaltyCalculatorController controller = new LoyaltyCalculatorController(
                new ServicePricingPolicyEvaluator());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        User user = new User(1, "bruce", "wayne");
        customer = new UsernamePasswordAuthenticationToken(
                user, user.getPassword(), Collections.emptyList());
    }

    @Test
    void runCwe94Experiment() throws Exception {
        String phase = System.getProperty("cwe94.phase", "mitigated");
        assertTrue("vulnerable".equals(phase) || "mitigated".equals(phase),
                "cwe94.phase must be vulnerable or mitigated");

        MvcResult baselineResult = calculate(BASELINE_PARTNER_CODE);
        MvcResult attackResult = calculate(ATTACK_PARTNER_CODE);

        JsonNode baselineResponse = objectMapper.readTree(
                baselineResult.getResponse().getContentAsString());
        JsonNode attackResponse = objectMapper.readTree(
                attackResult.getResponse().getContentAsString());

        BigDecimal baselinePrice = discountedPrice(baselineResponse);
        BigDecimal attackPrice = discountedPrice(attackResponse);
        boolean injectedLogicChangedPrice = attackPrice.compareTo(baselinePrice) != 0;

        writeEvidence(
                phase,
                baselineResult,
                attackResult,
                baselinePrice,
                attackPrice,
                injectedLogicChangedPrice);
        printEvidence(
                phase,
                baselineResult,
                attackResult,
                baselinePrice,
                attackPrice,
                injectedLogicChangedPrice);

        assertEquals(200, baselineResult.getResponse().getStatus());
        assertEquals(200, attackResult.getResponse().getStatus());
        assertEquals(0, baselinePrice.compareTo(new BigDecimal("10000.00")));

        if ("vulnerable".equals(phase)) {
            assertTrue(injectedLogicChangedPrice,
                    "injected partnerCode must change the evaluated pricing logic");
            assertEquals(0, attackPrice.compareTo(new BigDecimal("9500.00")));
            assertNotEquals(0, attackPrice.compareTo(baselinePrice));
        } else {
            assertEquals(0, attackPrice.compareTo(baselinePrice),
                    "injected SpEL text must remain ordinary partnerCode data");
            verifyLegitimatePartnerCodeStillWorks();
        }
    }

    private MvcResult calculate(String partnerCode) throws Exception {
        return mockMvc.perform(post("/loyalty-calculator")
                .principal(customer)
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("tier", "BRONZE")
                .param("laborPrice", "8000")
                .param("partsPrice", "2000")
                .param("estimatedDurationMinutes", "60")
                .param("completedServices", "1")
                .param("partnerCode", partnerCode))
                .andReturn();
    }

    private void verifyLegitimatePartnerCodeStillWorks() throws Exception {
        MvcResult result = calculate(VALID_PARTNER_CODE);
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, discountedPrice(response).compareTo(new BigDecimal("9500.00")));
    }

    private BigDecimal discountedPrice(JsonNode response) {
        return response.path("discountedPrice").decimalValue();
    }

    private void writeEvidence(String phase,
                               MvcResult baselineResult,
                               MvcResult attackResult,
                               BigDecimal baselinePrice,
                               BigDecimal attackPrice,
                               boolean injectedLogicChangedPrice) throws Exception {
        Path evidenceDir = Paths.get("attacks", "cwe-94", "evidence", phase);
        Files.createDirectories(evidenceDir);

        Files.write(evidenceDir.resolve("baseline-request.txt"),
                requestText(BASELINE_PARTNER_CODE).getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("baseline-response.json"),
                baselineResult.getResponse().getContentAsByteArray());
        Files.write(evidenceDir.resolve("attack-request.txt"),
                requestText(ATTACK_PARTNER_CODE).getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("attack-response.json"),
                attackResult.getResponse().getContentAsByteArray());
        Files.write(evidenceDir.resolve("result.txt"),
                resultText(
                        baselineResult.getResponse().getStatus(),
                        attackResult.getResponse().getStatus(),
                        baselinePrice,
                        attackPrice,
                        injectedLogicChangedPrice).getBytes(StandardCharsets.UTF_8));
    }

    private void printEvidence(String phase,
                               MvcResult baselineResult,
                               MvcResult attackResult,
                               BigDecimal baselinePrice,
                               BigDecimal attackPrice,
                               boolean injectedLogicChangedPrice) throws Exception {
        System.out.println("CWE94_EVIDENCE_BEGIN phase=" + phase);
        System.out.println("CWE94_BASELINE_RESPONSE="
                + baselineResult.getResponse().getContentAsString());
        System.out.println("CWE94_ATTACK_RESPONSE="
                + attackResult.getResponse().getContentAsString());
        System.out.print(resultText(
                baselineResult.getResponse().getStatus(),
                attackResult.getResponse().getStatus(),
                baselinePrice,
                attackPrice,
                injectedLogicChangedPrice));
        System.out.println("CWE94_EVIDENCE_END");
    }

    private String requestText(String partnerCode) {
        return "POST /loyalty-calculator\n"
                + "Content-Type: application/x-www-form-urlencoded\n\n"
                + "tier=BRONZE&laborPrice=8000&partsPrice=2000"
                + "&estimatedDurationMinutes=60&completedServices=1"
                + "&partnerCode=" + partnerCode + "\n";
    }

    private String resultText(int baselineStatus,
                              int attackStatus,
                              BigDecimal baselinePrice,
                              BigDecimal attackPrice,
                              boolean injectedLogicChangedPrice) {
        return "baselineHttpStatus=" + baselineStatus + "\n"
                + "attackHttpStatus=" + attackStatus + "\n"
                + "baselineDiscountedPrice=" + baselinePrice.toPlainString() + "\n"
                + "attackDiscountedPrice=" + attackPrice.toPlainString() + "\n"
                + "injectedLogicChangedPrice=" + injectedLogicChangedPrice + "\n";
    }
}
