package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDocumentGenerationTests {
    @TempDir
    Path tempDirectory;

    @Test
    void generatorCreatesAllThreePdfDocumentsWithServiceData() throws Exception {
        ServiceDocumentStorage storage = new ServiceDocumentStorage(tempDirectory.toString());
        ServiceDocumentGenerator generator = new ServiceDocumentGenerator(storage);
        Service service = completedService(127);
        ServiceDetails details = serviceDetails();

        generator.generate(service, details);

        Path serviceDirectory = storage.serviceDirectory(127);
        Path overview = serviceDirectory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW);
        Path parts = serviceDirectory.resolve(ServiceDocumentBundleService.PARTS_DETAILED);
        Path work = serviceDirectory.resolve(ServiceDocumentBundleService.WORK_DETAILED);

        assertThat(overview).exists().isRegularFile();
        assertThat(parts).exists().isRegularFile();
        assertThat(work).exists().isRegularFile();
        assertThat(Files.readAllBytes(overview)).startsWith("%PDF".getBytes("US-ASCII"));
        assertThat(Files.readAllBytes(parts)).startsWith("%PDF".getBytes("US-ASCII"));
        assertThat(Files.readAllBytes(work)).startsWith("%PDF".getBytes("US-ASCII"));

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
    void completingServiceTriggersDocumentGenerationAfterSqlTransition() {
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
