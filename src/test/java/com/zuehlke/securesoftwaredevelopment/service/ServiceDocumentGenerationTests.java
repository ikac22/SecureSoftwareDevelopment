package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDocumentGenerationTests {
    @TempDir
    Path tempDirectory;

    @Test
    void generatorPersistsAllThreeDocumentsInsideTarGzAndRemovesLoosePdfs() throws Exception {
        ServiceDocumentStorage storage = new ServiceDocumentStorage(tempDirectory.toString());
        ServiceDocumentGenerator generator = new ServiceDocumentGenerator(storage);
        Service service = completedService(127);
        ServiceDetails details = serviceDetails();

        generator.generate(service, details);

        Path serviceDirectory = storage.serviceDirectory(127);
        Path persistentArchive = storage.serviceArchive(127);
        assertThat(persistentArchive).exists().isRegularFile();
        assertThat(listGzipArchiveMembers(persistentArchive)).containsExactly(
                ServiceDocumentBundleService.SERVICE_OVERVIEW,
                ServiceDocumentBundleService.PARTS_DETAILED,
                ServiceDocumentBundleService.WORK_DETAILED);

        assertThat(serviceDirectory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW)).doesNotExist();
        assertThat(serviceDirectory.resolve(ServiceDocumentBundleService.PARTS_DETAILED)).doesNotExist();
        assertThat(serviceDirectory.resolve(ServiceDocumentBundleService.WORK_DETAILED)).doesNotExist();

        Path extracted = tempDirectory.resolve("generated-pdf-check");
        Files.createDirectories(extracted);
        Process extraction = new ProcessBuilder("tar", "-xzf", persistentArchive.toString(), "-C", extracted.toString())
                .redirectErrorStream(true)
                .start();
        assertThat(extraction.waitFor()).isZero();

        Path overview = extracted.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW);
        Path parts = extracted.resolve(ServiceDocumentBundleService.PARTS_DETAILED);
        Path work = extracted.resolve(ServiceDocumentBundleService.WORK_DETAILED);
        assertThat(Files.readAllBytes(overview)).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(Files.readAllBytes(parts)).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(Files.readAllBytes(work)).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));

        assertThat(extractText(overview))
                .contains("SERVICE OVERVIEW")
                .contains("Service ID: 127")
                .contains("Honda Civic")
                .contains("Oil change")
                .contains("TOTAL PRICE: 150.00");
        assertThat(extractText(parts))
                .contains("PARTS DETAILED")
                .contains("Oil filter")
                .contains("line total: 30.00");
        assertThat(extractText(work))
                .contains("WORK DETAILED")
                .contains("Oil change")
                .contains("Replace oil filter");
    }

    @Test
    void completingServiceTriggersDocumentArchivingAfterSqlTransition() {
        ServiceRepository repository = mock(ServiceRepository.class);
        TechnicianDirectory directory = mock(TechnicianDirectory.class);
        ServiceWorkService workService = mock(ServiceWorkService.class);
        ServiceDocumentGenerator generator = mock(ServiceDocumentGenerator.class);
        ServiceDetails details = serviceDetails();
        Service completed = completedService(127);

        when(workService.prepareForCompletion(127)).thenReturn(details);
        when(repository.complete(127)).thenReturn(true);
        when(repository.findById(127)).thenReturn(Optional.of(completed));

        ServiceWorkflowService workflow = new ServiceWorkflowService(
                repository, directory, workService, generator);

        workflow.complete(127);

        verify(workService).prepareForCompletion(127);
        verify(repository).complete(127);
        verify(generator).generate(completed, details);
    }

    private List<String> listGzipArchiveMembers(Path archive) throws Exception {
        Process process = new ProcessBuilder("tar", "-tzf", archive.toString())
                .redirectErrorStream(true)
                .start();
        List<String> members = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    members.add(line.trim());
                }
            }
        }
        assertThat(process.waitFor()).isZero();
        return members;
    }

    private Service completedService(int id) {
        return new Service(
                id,
                42,
                LocalDate.of(2026, 8, 23),
                LocalTime.of(10, 0),
                "Honda Civic",
                "Annual maintenance",
                ServiceStatus.COMPLETED,
                "marko.markovic",
                90,
                LocalDateTime.of(2026, 8, 23, 11, 30),
                null);
    }

    private ServiceDetails serviceDetails() {
        ServiceDetails details = new ServiceDetails(127);
        details.setTotalPrice(new BigDecimal("150.00"));

        ServiceDetails.PerformedService performed = new ServiceDetails.PerformedService();
        performed.setItemId("service-item-1");
        performed.setName("Oil change");
        performed.setDescription("Replace engine oil and filter");
        performed.setLaborPrice(new BigDecimal("120.00"));
        performed.setTotalPrice(new BigDecimal("150.00"));
        performed.setSubServices(Collections.singletonList(
                new ServiceDetails.PerformedSubService("sub-1", "Replace oil filter")));

        ServiceDetails.UsedPart part = new ServiceDetails.UsedPart();
        part.setItemId("part-item-1");
        part.setName("Oil filter");
        part.setManufacturer("Demo Parts");
        part.setPartNumber("OF-42");
        part.setQuantity(BigDecimal.ONE);
        part.setUnit("piece");
        part.setUnitPrice(new BigDecimal("30.00"));
        part.setLineTotal(new BigDecimal("30.00"));
        performed.setUsedParts(Collections.singletonList(part));

        details.setPerformedServices(Collections.singletonList(performed));
        return details;
    }

    private String extractText(Path pdf) throws Exception {
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }
}
