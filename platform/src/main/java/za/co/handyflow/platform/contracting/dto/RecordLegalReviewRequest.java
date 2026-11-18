package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** A South African legal practitioner's review of one template version. Recorded by staff on the reviewer's behalf. */
public record RecordLegalReviewRequest(
        @NotBlank String reviewerName,
        @NotBlank String reviewerProfessionalDetails,   // e.g. admitted attorney, Law Society registration
        String reviewerOrganisation,
        @NotNull LocalDate reviewDate,
        @NotBlank String legalArea,
        String jurisdiction,                            // defaults to South Africa
        String scopeAssumptions,
        String outstandingIssues,                       // required for CONDITIONAL
        @NotBlank String outcome,                       // APPROVED, REJECTED or CONDITIONAL
        LocalDate nextReviewDate,
        UUID evidenceDocumentId
) {}
