package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ServiceDocumentGenerator {
    private static final float FONT_SIZE = 10f;
    private static final float LEADING = 14f;
    private static final float MARGIN = 50f;
    private static final int MAX_LINE_LENGTH = 88;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ServiceDocumentStorage storage;

    public ServiceDocumentGenerator(ServiceDocumentStorage storage) {
        this.storage = storage;
    }

    public void generate(Service service, ServiceDetails details) {
        if (service == null || service.getId() == null) {
            throw new IllegalArgumentException("Service is required for document generation");
        }
        if (details == null) {
            throw new IllegalArgumentException("Service details are required for document generation");
        }

        Path directory = storage.serviceDirectory(service.getId());
        List<Path> generatedDocuments = Arrays.asList(
                directory.resolve(ServiceDocumentBundleService.SERVICE_OVERVIEW),
                directory.resolve(ServiceDocumentBundleService.PARTS_DETAILED),
                directory.resolve(ServiceDocumentBundleService.WORK_DETAILED));
        try {
            Files.createDirectories(directory);
            writePdf(generatedDocuments.get(0), overviewLines(service, details));
            writePdf(generatedDocuments.get(1), partsLines(service, details));
            writePdf(generatedDocuments.get(2), workLines(service, details));
            createPersistentArchive(service.getId(), directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate service documents", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Service document archiving was interrupted", exception);
        } finally {
            for (Path document : generatedDocuments) {
                try {
                    Files.deleteIfExists(document);
                } catch (IOException ignored) {
                    // Best-effort cleanup; the persistent archive is the source of truth after completion.
                }
            }
        }
    }

    private void createPersistentArchive(int serviceId, Path directory)
            throws IOException, InterruptedException {
        Path archive = storage.serviceArchive(serviceId);
        Path temporaryArchive = Files.createTempFile(directory, "service-documents-", ".tar.gz");
        try {
            Process process = new ProcessBuilder(
                    "tar", "-czf", temporaryArchive.toString(),
                    ServiceDocumentBundleService.SERVICE_OVERVIEW,
                    ServiceDocumentBundleService.PARTS_DETAILED,
                    ServiceDocumentBundleService.WORK_DETAILED)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = readOutput(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("tar failed with exit code " + exitCode + ": " + output);
            }
            Files.move(temporaryArchive, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryArchive);
        }
    }

    private String readOutput(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private List<String> overviewLines(Service service, ServiceDetails details) {
        List<String> lines = new ArrayList<>();
        lines.add("SERVICE OVERVIEW");
        lines.add("");
        lines.add("Service ID: " + service.getId());
        lines.add("Car: " + value(service.getCarModel()));
        lines.add("Description: " + value(service.getDescription()));
        lines.add("Technician: " + value(service.getTechnician()));
        lines.add("Scheduled date: " + value(service.getDate()));
        lines.add("Scheduled time: " + value(service.getTime()));
        lines.add("Estimated duration: " + value(service.getEstimatedDurationMinutes()) + " minutes");
        lines.add("Completed at: " + (service.getCompletedAt() == null
                ? "-" : service.getCompletedAt().format(DATE_TIME_FORMAT)));
        lines.add("");
        lines.add("Performed services:");
        for (ServiceDetails.PerformedService performed : details.getPerformedServices()) {
            lines.add("- " + value(performed.getName()) + " | total: " + money(performed.getTotalPrice()));
        }
        lines.add("");
        lines.add("TOTAL PRICE: " + money(details.getTotalPrice()));
        return lines;
    }

    private List<String> partsLines(Service service, ServiceDetails details) {
        List<String> lines = new ArrayList<>();
        lines.add("PARTS DETAILED");
        lines.add("Service ID: " + service.getId());
        lines.add("");

        boolean hasParts = false;
        for (ServiceDetails.PerformedService performed : details.getPerformedServices()) {
            if (performed.getUsedParts().isEmpty()) {
                continue;
            }
            hasParts = true;
            lines.add("Service: " + value(performed.getName()));
            for (ServiceDetails.UsedPart part : performed.getUsedParts()) {
                lines.add("- " + value(part.getName())
                        + " | manufacturer: " + value(part.getManufacturer())
                        + " | part no: " + value(part.getPartNumber()));
                lines.add("  quantity: " + value(part.getQuantity()) + " " + value(part.getUnit())
                        + " | unit price: " + money(part.getUnitPrice())
                        + " | line total: " + money(part.getLineTotal()));
                if (part.getComment() != null && !part.getComment().trim().isEmpty()) {
                    lines.add("  comment: " + part.getComment().trim());
                }
            }
            lines.add("");
        }
        if (!hasParts) {
            lines.add("No parts were recorded for this service.");
        }
        return lines;
    }

    private List<String> workLines(Service service, ServiceDetails details) {
        List<String> lines = new ArrayList<>();
        lines.add("WORK DETAILED");
        lines.add("Service ID: " + service.getId());
        lines.add("");

        for (ServiceDetails.PerformedService performed : details.getPerformedServices()) {
            lines.add(value(performed.getName()));
            lines.add("Description: " + value(performed.getDescription()));
            lines.add("Labor price: " + money(performed.getLaborPrice()));
            lines.add("Service total: " + money(performed.getTotalPrice()));
            if (!performed.getSubServices().isEmpty()) {
                lines.add("Sub-services:");
                for (ServiceDetails.PerformedSubService subService : performed.getSubServices()) {
                    lines.add("- " + value(subService.getName()));
                }
            }
            if (performed.getComment() != null && !performed.getComment().trim().isEmpty()) {
                lines.add("Comment: " + performed.getComment().trim());
            }
            lines.add("");
        }
        return lines;
    }

    private void writePdf(Path target, List<String> sourceLines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = openContent(document, page);
            float y = page.getMediaBox().getHeight() - MARGIN;

            for (String sourceLine : sourceLines) {
                for (String line : wrap(asciiSafe(sourceLine))) {
                    if (y < MARGIN + LEADING) {
                        content.endText();
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = openContent(document, page);
                        y = page.getMediaBox().getHeight() - MARGIN;
                    }
                    content.showText(line);
                    content.newLineAtOffset(0, -LEADING);
                    y -= LEADING;
                }
            }

            content.endText();
            content.close();
            document.save(target.toFile());
        }
    }

    private PDPageContentStream openContent(PDDocument document, PDPage page) throws IOException {
        PDPageContentStream content = new PDPageContentStream(document, page);
        content.beginText();
        content.setFont(PDType1Font.HELVETICA, FONT_SIZE);
        content.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
        return content;
    }

    private List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        String remaining = text == null ? "" : text;
        if (remaining.isEmpty()) {
            lines.add("");
            return lines;
        }
        while (remaining.length() > MAX_LINE_LENGTH) {
            int split = remaining.lastIndexOf(' ', MAX_LINE_LENGTH);
            if (split <= 0) {
                split = MAX_LINE_LENGTH;
            }
            lines.add(remaining.substring(0, split));
            remaining = remaining.substring(split).trim();
        }
        lines.add(remaining);
        return lines;
    }

    private String asciiSafe(String value) {
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            builder.append(ch >= 32 && ch <= 126 ? ch : '?');
        }
        return builder.toString();
    }

    private String value(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }
}
