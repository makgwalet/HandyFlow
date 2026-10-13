package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeReviewStatus;

/** outcome must be ACCEPTED or REJECTED; the reviewer must differ from whoever recorded the notice. */
public record NoticeReviewRequest(@NotNull NoticeReviewStatus outcome, @NotBlank String note) {}
