package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.Size;

public record SwapActionRequest(
        @Size(max = 500) String reason  // for reject: rejection reason; for approve: optional note
) {}
