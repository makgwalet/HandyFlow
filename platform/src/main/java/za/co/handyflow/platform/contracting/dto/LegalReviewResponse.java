package za.co.handyflow.platform.contracting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LegalReviewResponse(
        UUID id,
        UUID templateId,
        int templateVersion,
        String reviewerName,
        String reviewerProfessionalDetails,
        String reviewerOrganisation,
        LocalDate reviewDate,
        String legalArea,
        String jurisdiction,
        String scopeAssumptions,
        String outstandingIssues,
        String outcome,
        LocalDate nextReviewDate,
        UUID evidenceDocumentId,
        String evidenceFileName,
        boolean expired,
        Instant withdrawnAt,
        String withdrawnReason,
        Instant createdAt
) {}
