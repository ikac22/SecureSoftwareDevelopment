package com.zuehlke.securesoftwaredevelopment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid}")
@AutoConfigureMockMvc
class Cwe90ExperimentTests {
    private static final String DATE = "2030-06-01";
    private static final String DURATION = "60";
    private static final String TRUE_PROBE = "*)(mail=m*)(cn=*";
    private static final String FALSE_PROBE = "*)(mail=z*)(cn=*";
    private static final String NORMAL_SEARCH = "Jovanovic";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runCwe90Experiment() throws Exception {
        String phase = System.getProperty("cwe90.phase", "mitigated");
        assertTrue("vulnerable".equals(phase) || "mitigated".equals(phase),
                "cwe90.phase must be vulnerable or mitigated");

        MvcResult trueResult = performSearch(TRUE_PROBE);
        MvcResult falseResult = performSearch(FALSE_PROBE);

        JsonNode trueResponse = objectMapper.readTree(trueResult.getResponse().getContentAsString());
        JsonNode falseResponse = objectMapper.readTree(falseResult.getResponse().getContentAsString());

        int trueCount = trueResponse.isArray() ? trueResponse.size() : -1;
        int falseCount = falseResponse.isArray() ? falseResponse.size() : -1;
        boolean predicateOracleObserved = trueCount > 0 && falseCount == 0;

        boolean positiveControlPassed = false;
        if ("mitigated".equals(phase)) {
            positiveControlPassed = verifyNormalSearchStillWorks();
        }

        writeEvidence(phase, trueResult, falseResult, trueCount, falseCount,
                predicateOracleObserved, positiveControlPassed);
        printEvidence(phase, trueResult, falseResult, trueCount, falseCount,
                predicateOracleObserved, positiveControlPassed);

        assertEquals(200, trueResult.getResponse().getStatus());
        assertEquals(200, falseResult.getResponse().getStatus());

        if ("vulnerable".equals(phase)) {
            assertTrue(predicateOracleObserved,
                    "injected mail predicate must create a non-empty/empty LDAP oracle");
        } else {
            assertFalse(predicateOracleObserved,
                    "escaped LDAP metacharacters must not preserve the injected predicate oracle");
            assertEquals(trueCount, falseCount,
                    "true and false injected predicates must no longer change LDAP filter structure");
            assertTrue(positiveControlPassed,
                    "legitimate technician substring search must still work after mitigation");
        }
    }

    private MvcResult performSearch(String search) throws Exception {
        return mockMvc.perform(get("/services/1/available-slots")
                .with(user("service-manager"))
                .param("date", DATE)
                .param("estimatedDurationMinutes", DURATION)
                .param("search", search))
                .andReturn();
    }

    private boolean verifyNormalSearchStillWorks() throws Exception {
        MvcResult result = performSearch(NORMAL_SEARCH);
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return result.getResponse().getStatus() == 200
                && response.isArray()
                && response.size() == 1
                && "jelena.jovanovic".equals(response.get(0).path("technician").path("id").asText());
    }

    private void writeEvidence(String phase,
                               MvcResult trueResult,
                               MvcResult falseResult,
                               int trueCount,
                               int falseCount,
                               boolean predicateOracleObserved,
                               boolean positiveControlPassed) throws Exception {
        Path evidenceDir = Paths.get("attacks", "cwe-90", "evidence", phase);
        Files.createDirectories(evidenceDir);

        Files.write(evidenceDir.resolve("true-probe-request.txt"),
                requestText(TRUE_PROBE).getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("true-probe-response.json"),
                trueResult.getResponse().getContentAsByteArray());
        Files.write(evidenceDir.resolve("false-probe-request.txt"),
                requestText(FALSE_PROBE).getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("false-probe-response.json"),
                falseResult.getResponse().getContentAsByteArray());
        Files.write(evidenceDir.resolve("result.txt"),
                resultText(trueResult, falseResult, trueCount, falseCount,
                        predicateOracleObserved, positiveControlPassed)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private void printEvidence(String phase,
                               MvcResult trueResult,
                               MvcResult falseResult,
                               int trueCount,
                               int falseCount,
                               boolean predicateOracleObserved,
                               boolean positiveControlPassed) throws Exception {
        System.out.println("CWE90_EVIDENCE_BEGIN phase=" + phase);
        System.out.println("CWE90_TRUE_REQUEST=" + requestText(TRUE_PROBE));
        System.out.println("CWE90_TRUE_RESPONSE=" + trueResult.getResponse().getContentAsString());
        System.out.println("CWE90_FALSE_REQUEST=" + requestText(FALSE_PROBE));
        System.out.println("CWE90_FALSE_RESPONSE=" + falseResult.getResponse().getContentAsString());
        System.out.print(resultText(trueResult, falseResult, trueCount, falseCount,
                predicateOracleObserved, positiveControlPassed));
        System.out.println("CWE90_EVIDENCE_END");
    }

    private String requestText(String search) {
        return "GET /services/1/available-slots?date=" + DATE
                + "&estimatedDurationMinutes=" + DURATION
                + "&search=" + search + "\n";
    }

    private String resultText(MvcResult trueResult,
                              MvcResult falseResult,
                              int trueCount,
                              int falseCount,
                              boolean predicateOracleObserved,
                              boolean positiveControlPassed) {
        return "trueProbeHttpStatus=" + trueResult.getResponse().getStatus() + "\n"
                + "falseProbeHttpStatus=" + falseResult.getResponse().getStatus() + "\n"
                + "trueProbeResultCount=" + trueCount + "\n"
                + "falseProbeResultCount=" + falseCount + "\n"
                + "predicateOracleObserved=" + predicateOracleObserved + "\n"
                + "positiveControlPassed=" + positiveControlPassed + "\n";
    }
}
