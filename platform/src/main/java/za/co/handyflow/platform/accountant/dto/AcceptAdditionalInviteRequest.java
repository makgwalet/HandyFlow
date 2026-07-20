package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptAdditionalInviteRequest(
        @NotBlank String inviteToken
) {
}