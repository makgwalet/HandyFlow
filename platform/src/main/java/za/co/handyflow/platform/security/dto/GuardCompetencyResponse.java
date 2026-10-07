// security/dto/GuardCompetencyResponse.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A competency with its computed state ("MET", "EXPIRED", ...) and evidence files. */
public record GuardCompetencyResponse(
        UUID id, UUID guardId, String competencyType, String label, String title, String issuedBy,
        LocalDate issueDate, LocalDate expiryDate, String certificateRef, boolean required, String notes,
        String state, String detail,
        String verifiedByName, Instant verifiedAt, String verificationNote,
        List<GuardOverviewResponse.EvidenceItem> evidence, Instant createdAt
) {}
