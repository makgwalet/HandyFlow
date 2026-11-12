package za.co.handyflow.platform.fuel.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ComplianceResponse(UUID id, String itemType, String title, String referenceNo, String subjectType, UUID subjectId,
                                 String subjectLabel, LocalDate issuedOn, LocalDate expiresOn, String notes,
                                 String status, long daysToExpiry, UUID legalEntityId, String legalEntityName, String licensedActivity,
                                 String issuingAuthority, long evidenceCount, UUID depotId, String depotName) {
    public ComplianceResponse(UUID id, String itemType, String title, String referenceNo, String subjectType, UUID subjectId,
                              String subjectLabel, LocalDate issuedOn, LocalDate expiresOn, String notes,
                              String status, long daysToExpiry, UUID legalEntityId, String legalEntityName, String licensedActivity,
                              String issuingAuthority, long evidenceCount) {
        this(id, itemType, title, referenceNo, subjectType, subjectId, subjectLabel, issuedOn, expiresOn, notes, status, daysToExpiry,
                legalEntityId, legalEntityName, licensedActivity, issuingAuthority, evidenceCount, null, null);
    }
}
