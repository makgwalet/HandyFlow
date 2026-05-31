package za.co.handyflow.platform.desk.dto;

import jakarta.validation.constraints.NotBlank;

public record DeskAddCommentRequest(
        @NotBlank String body,
                  boolean internal,    // true = staff-only note, not visible to customer
                  String  authorName   // used by public (customer) endpoint only
) {}
