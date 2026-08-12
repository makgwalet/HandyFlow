package za.co.handyflow.platform.payrollbureau.dto;

import java.time.Instant;
import java.util.UUID;

public record PayEmployeeDocumentResponse(
        UUID id, String docType, String fileName, Long fileSizeBytes, Instant uploadedAt
) {}