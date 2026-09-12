package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.service.PersonalGalleryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class Cwe610ExperimentTests {
    private static final String READ_PATH = "../outside-read.txt";
    private static final String READ_MARKER = "CWE610_OUTSIDE_READ_FIXTURE";
    private static final String OVERWRITE_PATH = "../../outside-write.jpg";
    private static final String TARGET_BEFORE = "CWE610_TARGET_BEFORE";
    private static final String TARGET_AFTER = "CWE610_TARGET_AFTER";

    @TempDir
    Path tempDirectory;

    private MockMvc mockMvc;
    private Authentication customer;
    private Path galleryRoot;
    private Path outsideWriteTarget;

    @BeforeEach
    void setUp() throws Exception {
        galleryRoot = tempDirectory.resolve("user-galleries");
        Files.createDirectories(galleryRoot.resolve("1"));
        Files.write(tempDirectory.resolve("outside-read.txt"), READ_MARKER.getBytes(StandardCharsets.UTF_8));
        outsideWriteTarget = tempDirectory.resolve("outside-write.jpg");
        Files.write(outsideWriteTarget, TARGET_BEFORE.getBytes(StandardCharsets.UTF_8));

        PersonalGalleryService service = new PersonalGalleryService(galleryRoot.toString());
        PersonalGalleryController controller = new PersonalGalleryController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        User user = new User(1, "bruce", "wayne");
        customer = new UsernamePasswordAuthenticationToken(
                user, user.getPassword(), Collections.emptyList());
    }

    @Test
    void runCwe610Experiment() throws Exception {
        String phase = System.getProperty("cwe610.phase", "mitigated");
        assertTrue("vulnerable".equals(phase) || "mitigated".equals(phase),
                "cwe610.phase must be vulnerable or mitigated");

        MvcResult readResult = mockMvc.perform(get("/gallery/image")
                .param("path", READ_PATH))
                .andReturn();
        String readBody = readResult.getResponse().getContentAsString();
        boolean readTraversalReturnedOutsideResource = readBody.contains(READ_MARKER);

        MvcResult overwriteProbe = performUpload(OVERWRITE_PATH, false, TARGET_AFTER);
        String overwriteProbeBody = overwriteProbe.getResponse().getContentAsString();
        boolean overwriteProbeRecognizedOutsideTarget = overwriteProbeBody.contains("REQUIRES_OVERWRITE");

        MvcResult overwriteResult = performUpload(OVERWRITE_PATH, true, TARGET_AFTER);
        String overwriteBody = overwriteResult.getResponse().getContentAsString();
        String targetAfter = new String(Files.readAllBytes(outsideWriteTarget), StandardCharsets.UTF_8);
        boolean outsideTargetModified = TARGET_AFTER.equals(targetAfter);

        boolean positiveControlPassed = verifyLegitimateUploadOverwriteAndDisplay();

        writeEvidence(
                phase,
                readResult.getResponse().getStatus(),
                readBody,
                readTraversalReturnedOutsideResource,
                overwriteProbe.getResponse().getStatus(),
                overwriteProbeBody,
                overwriteProbeRecognizedOutsideTarget,
                overwriteResult.getResponse().getStatus(),
                overwriteBody,
                targetAfter,
                outsideTargetModified,
                positiveControlPassed);

        printEvidence(
                phase,
                readResult.getResponse().getStatus(),
                readTraversalReturnedOutsideResource,
                overwriteProbe.getResponse().getStatus(),
                overwriteProbeRecognizedOutsideTarget,
                overwriteResult.getResponse().getStatus(),
                outsideTargetModified,
                positiveControlPassed);

        if ("vulnerable".equals(phase)) {
            assertEquals(200, readResult.getResponse().getStatus());
            assertTrue(readTraversalReturnedOutsideResource,
                    "read traversal must return the harmless fixture outside galleryRoot");
            assertEquals(409, overwriteProbe.getResponse().getStatus());
            assertTrue(overwriteProbeRecognizedOutsideTarget,
                    "overwrite=false must reveal that the outside target already exists");
            assertEquals(200, overwriteResult.getResponse().getStatus());
            assertTrue(outsideTargetModified,
                    "overwrite=true must replace the existing harmless target outside the user gallery");
        } else {
            assertFalse(readTraversalReturnedOutsideResource,
                    "mitigation must prevent reading outside galleryRoot");
            assertFalse(overwriteProbeRecognizedOutsideTarget,
                    "validation must reject the traversal before probing the outside target");
            assertFalse(outsideTargetModified,
                    "mitigation must leave the outside target unchanged");
        }

        assertTrue(positiveControlPassed,
                "regular upload, overwrite, and display inside the personal gallery must still work");
    }

    private MvcResult performUpload(String fileName, boolean overwrite, String content) throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "upload.jpg",
                "image/jpeg",
                content.getBytes(StandardCharsets.UTF_8));

        return mockMvc.perform(multipart("/my-gallery/upload")
                .file(image)
                .param("fileName", fileName)
                .param("overwrite", Boolean.toString(overwrite))
                .principal(customer))
                .andReturn();
    }

    private boolean verifyLegitimateUploadOverwriteAndDisplay() throws Exception {
        String legitimatePath = "1/legitimate.jpg";

        MvcResult createResult = performUpload("legitimate.jpg", false, "LEGITIMATE_INITIAL");
        MvcResult probeResult = performUpload("legitimate.jpg", false, "LEGITIMATE_UPDATED");
        MvcResult overwriteResult = performUpload("legitimate.jpg", true, "LEGITIMATE_UPDATED");
        MvcResult displayResult = mockMvc.perform(get("/gallery/image")
                .param("path", legitimatePath))
                .andReturn();

        String displayBody = displayResult.getResponse().getContentAsString();
        return createResult.getResponse().getStatus() == 200
                && createResult.getResponse().getContentAsString().contains("CREATED")
                && probeResult.getResponse().getStatus() == 409
                && probeResult.getResponse().getContentAsString().contains("REQUIRES_OVERWRITE")
                && overwriteResult.getResponse().getStatus() == 200
                && overwriteResult.getResponse().getContentAsString().contains("OVERWRITTEN")
                && displayResult.getResponse().getStatus() == 200
                && displayBody.contains("LEGITIMATE_UPDATED");
    }

    private void writeEvidence(String phase,
                               int readStatus,
                               String readBody,
                               boolean readTraversalReturnedOutsideResource,
                               int overwriteProbeStatus,
                               String overwriteProbeBody,
                               boolean overwriteProbeRecognizedOutsideTarget,
                               int overwriteStatus,
                               String overwriteBody,
                               String targetAfter,
                               boolean outsideTargetModified,
                               boolean positiveControlPassed) throws Exception {
        Path evidenceDir = Paths.get("attacks", "cwe-610", "evidence", phase);
        Files.createDirectories(evidenceDir);

        Files.write(evidenceDir.resolve("read-request.txt"),
                ("GET /gallery/image?path=" + READ_PATH + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("read-response.bin"), readBody.getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("overwrite-probe-request.txt"),
                uploadRequestText(false).getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("overwrite-probe-response.json"),
                overwriteProbeBody.getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("overwrite-request.txt"),
                uploadRequestText(true).getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("overwrite-response.json"),
                overwriteBody.getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("target-before.txt"), TARGET_BEFORE.getBytes(StandardCharsets.UTF_8));
        Files.write(evidenceDir.resolve("target-after.txt"), targetAfter.getBytes(StandardCharsets.UTF_8));

        String result = resultText(
                readStatus,
                readTraversalReturnedOutsideResource,
                overwriteProbeStatus,
                overwriteProbeRecognizedOutsideTarget,
                overwriteStatus,
                outsideTargetModified,
                positiveControlPassed);
        Files.write(evidenceDir.resolve("result.txt"), result.getBytes(StandardCharsets.UTF_8));
    }

    private String uploadRequestText(boolean overwrite) {
        return "POST /my-gallery/upload\n"
                + "fileName=" + OVERWRITE_PATH + "\n"
                + "overwrite=" + overwrite + "\n"
                + "imageContent=" + TARGET_AFTER + "\n";
    }

    private void printEvidence(String phase,
                               int readStatus,
                               boolean readTraversalReturnedOutsideResource,
                               int overwriteProbeStatus,
                               boolean overwriteProbeRecognizedOutsideTarget,
                               int overwriteStatus,
                               boolean outsideTargetModified,
                               boolean positiveControlPassed) {
        System.out.println("CWE610_EVIDENCE_BEGIN phase=" + phase);
        System.out.print(resultText(
                readStatus,
                readTraversalReturnedOutsideResource,
                overwriteProbeStatus,
                overwriteProbeRecognizedOutsideTarget,
                overwriteStatus,
                outsideTargetModified,
                positiveControlPassed));
        System.out.println("CWE610_EVIDENCE_END");
    }

    private String resultText(int readStatus,
                              boolean readTraversalReturnedOutsideResource,
                              int overwriteProbeStatus,
                              boolean overwriteProbeRecognizedOutsideTarget,
                              int overwriteStatus,
                              boolean outsideTargetModified,
                              boolean positiveControlPassed) {
        return "readHttpStatus=" + readStatus + "\n"
                + "readTraversalReturnedOutsideResource=" + readTraversalReturnedOutsideResource + "\n"
                + "overwriteProbeHttpStatus=" + overwriteProbeStatus + "\n"
                + "overwriteProbeRecognizedOutsideTarget=" + overwriteProbeRecognizedOutsideTarget + "\n"
                + "overwriteHttpStatus=" + overwriteStatus + "\n"
                + "outsideTargetModified=" + outsideTargetModified + "\n"
                + "positiveControlPassed=" + positiveControlPassed + "\n";
    }
}
