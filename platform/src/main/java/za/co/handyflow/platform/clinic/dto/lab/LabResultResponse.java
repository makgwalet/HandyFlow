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
        Instant createdAt,
        boolean hasAbnormal,
        boolean hasCritical
) {
    /** Without the roll-up flags, for callers that predate them. */
    public LabResultResponse(UUID id, UUID patientId, UUID consultationId, String source, String labReference,
                             Instant collectedAt, Instant receivedAt, String pdfUrl, String pdfFilename, String status,
                             String patientNameRaw, String parsedMarkersJson, String interpretation,
                             boolean notified, Instant createdAt) {
        this(id, patientId, consultationId, source, labReference, collectedAt, receivedAt, pdfUrl, pdfFilename, status,
                patientNameRaw, parsedMarkersJson, interpretation, notified, createdAt, false, false);
    }
}
