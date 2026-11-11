package za.co.handyflow.platform.fuel.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ComplianceResponse(UUID id, String itemType, String title, String referenceNo, String subjectType, UUID subjectId,
                                 String subjectLabel, LocalDate issuedOn, LocalDate expiresOn, String notes,
                                 String status, long daysToExpiry) {}
