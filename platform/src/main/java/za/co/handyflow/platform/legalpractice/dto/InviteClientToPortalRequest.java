package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Staff invite a client contact to the portal by email — the standard
 * firm-invites-first flow, matching {@code AccPortalAccessGrant}/
 * {@code AuditorAccessGrant}'s own confirmed shape now that
 * {@code LpPortalAccessGrant} carries a real invite-token/email/status
 * state machine. Replaces the earlier {@code GrantPortalAccessRequest},
 * which assumed an already-registered {@code PortalUser} looked up by
 * email — no longer needed now that a grant can exist PENDING before any
 * {@code PortalUser} does.
 */
public record InviteClientToPortalRequest(@NotBlank @Email String inviteEmail) {}
