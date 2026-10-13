package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;

/** Free-text record of the prescription review (period considered, interruptions, conclusion). The system does not compute prescription. */
public record PrescriptionReviewRequest(@NotBlank String note) {}
