package za.co.handyflow.platform.clinic.dto.lab;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LabResultResponse(
        UUID   id,
        UUID   patientId,
        UUID   consultationId,
        String source,
        String labReference,
        Instant collectedAt,
        Instant receivedAt,
        String pdfUrl,
        String pdfFilename,
        String status,
        String patientNameRaw,
        String parsedMarkersJson,   // raw JSON string from DB
        String interpretation,
        boolean notified,
        Instant createdAt
) {}
