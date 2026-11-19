package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The clause library. Clauses are supplied by the tenant's legal reviewer; none are built in. */
public final class ContractClauseDtos {
    private ContractClauseDtos() {}

    /** clauseKind: STANDARD (the default), CUSTOM, REGULATORY, SENSITIVE or SOURCED. Anything but STANDARD needs evidence before publishing; SOURCED also needs sourceReference. */
    public record ClauseRequest(@NotBlank String title, @NotBlank String category, @NotBlank String body, String legalArea, String jurisdiction,
                                String clauseKind, String sourceReference) {}

    public record ApproveClauseRequest(@NotBlank String reviewerName, @NotBlank String reviewerProfessionalDetails, @NotNull LocalDate reviewDate,
                                       LocalDate nextReviewDate, String notes) {}

    public record WithdrawClauseRequest(@NotBlank String reason) {}

    /** {@code afterBlock}: null puts the clause at the end, -1 at the start, n after the n-th block (counting from 0). */
    public record InsertClauseRequest(@NotNull UUID clauseId, Integer afterBlock, String note) {}

    /** legalStatus: NOT_REVIEWED, REVIEWED, REVIEW_EXPIRED, SUPERSEDED or WITHDRAWN. A review is evidence, not a compliance guarantee. */
    public record ClauseResponse(UUID id, UUID familyId, int versionNo, String title, String category, String body, String legalArea, String jurisdiction,
                                 String status, String legalStatus, String reviewerName, String reviewerProfessionalDetails, LocalDate reviewDate,
                                 LocalDate nextReviewDate, String reviewNotes, Instant publishedAt, String withdrawnReason, boolean usable,
                                 UUID evidenceDocumentId, String evidenceFileName,
                                 String clauseKind, String sourceReference, boolean evidenceRequired, String evidenceRequiredReason) {}

    /** state: CURRENT, SUPERSEDED, WITHDRAWN or REVIEW_EXPIRED, judged today against the clause as it stands now. */
    public record ClauseUsageResponse(UUID clauseId, String title, int clauseVersionNo, String state, Integer contractVersionNo, Instant insertedAt) {}
}
