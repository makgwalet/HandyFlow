package za.co.handyflow.platform.clinic.dto.lab;

import java.time.Instant;

// FIX #3 — added collectedAt (was being sent by LabsTabEnhanced but silently dropped)
public record UploadLabResultRequest(
        String  source,         // AMPATH | LANCET | PATHCARE | VERMAAK | EMAIL | MANUAL
        String  pdfUrl,
        String  pdfFilename,
        String  patientNameRaw,
        String  labReference,
        Instant collectedAt     // specimen collection date — optional
) {}
