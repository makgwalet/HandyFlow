package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers the shared {@code PortalUser} identity by redeeming a live
 * {@code LpPortalAccessGrant} invite token — direct mirror of
 * {@code AuditorPortalAuthController.RegisterRequest}'s own confirmed
 * shape. Replaces the earlier bare, no-token {@code PortalRegisterRequest}
 * now that the grant entity actually carries an invite-token/email/status
 * state machine to redeem against.
 */
public record RegisterViaInviteRequest(
        @NotBlank String inviteToken,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName
) {}
