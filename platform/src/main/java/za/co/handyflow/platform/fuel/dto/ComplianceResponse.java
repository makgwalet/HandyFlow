package za.co.handyflow.platform.fuel.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ComplianceResponse(UUID id, String itemType, String title, String referenceNo, String subjectType, UUID subjectId,
                                 String subjectLabel, LocalDate issuedOn, LocalDate expiresOn, String notes,
                                 String status, long daysToExpiry, UUID legalEntityId, String legalEntityName, String licensedActivity,
                                 String issuingAuthority, long evidenceCount, UUID depotId, String depotName, boolean detailsHidden) {
    public ComplianceResponse(UUID id, String itemType, String title, String referenceNo, String subjectType, UUID subjectId,
                              String subjectLabel, LocalDate issuedOn, LocalDate expiresOn, String notes,
                              String status, long daysToExpiry, UUID legalEntityId, String legalEntityName, String licensedActivity,
                              String issuingAuthority, long evidenceCount, UUID depotId, String depotName) {
        this(id, itemType, title, referenceNo, subjectType, subjectId, subjectLabel, issuedOn, expiresOn, notes, status, daysToExpiry,
                legalEntityId, legalEntityName, licensedActivity, issuingAuthority, evidenceCount, depotId, depotName, false);
    }
    public ComplianceResponse(UUID id, String itemType, String title, String referenceNo, String subjectType, UUID subjectId,
                              String subjectLabel, LocalDate issuedOn, LocalDate expiresOn, String notes,
                              String status, long daysToExpiry, UUID legalEntityId, String legalEntityName, String licensedActivity,
                              String issuingAuthority, long evidenceCount) {
        this(id, itemType, title, referenceNo, subjectType, subjectId, subjectLabel, issuedOn, expiresOn, notes, status, daysToExpiry,
                legalEntityId, legalEntityName, licensedActivity, issuingAuthority, evidenceCount, null, null, false);
    }

    /** What a person who may only see the status gets: what it is, who or where it is for, when it expires and its status. Nothing else. */
    public ComplianceResponse statusOnly() {
        return new ComplianceResponse(id, itemType, title, null, subjectType, subjectId, subjectLabel, null, expiresOn, null, status, daysToExpiry,
                legalEntityId, legalEntityName, licensedActivity, null, 0L, depotId, depotName, true);
    }
}
