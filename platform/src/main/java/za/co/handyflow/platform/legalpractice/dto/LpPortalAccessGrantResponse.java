package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Direct mirror of the confirmed-real shape shared by every sibling
 * client-scoped portal module (accountant/bookingagency/payrollbureau/
 * recruitmentagency's own {@code PortalAccessGrantResponse} — all read
 * directly from real source this session, all identical). Replaces the
 * earlier required-portalUserId shape now that {@code LpPortalAccessGrant}
 * is invite-token/email/status based; {@code portalUserId} is dropped from
 * the response for the same reason none of the sibling DTOs expose it —
 * it's null until acceptance and irrelevant to the staff-side view.
 */
public record LpPortalAccessGrantResponse(
        UUID id,
        String inviteEmail,
        String status,
        Instant invitedAt,
        Instant acceptedAt,
        Instant revokedAt
) {}
