package za.co.handyflow.platform.notifications.application;

import java.util.UUID;

/**
 * A notification target, described entirely by the caller.
 * <p>
 * WHY doesn't this module just take a {@code userId} and look up the
 * email/phone itself? Because that would mean the notifications module
 * depends on your Identity/User module (to resolve contact details), and
 * probably your Security module too (to know who has which role). That's a
 * dependency pointing the wrong way — "shared" modules should be depended
 * upon, not depend on business modules themselves. It also becomes a mess
 * the moment you want to notify someone who ISN'T a platform user (e.g. an
 * external hire-supplier contact by email only, with no {@code userId}).
 * <p>
 * Instead, the calling module (earthmoving, billing, whatever) resolves who
 * needs to know and what their contact details are — it already has that
 * context — and hands this module a flat, self-contained {@code Recipient}.
 * This module's only job is deciding HOW to reach them (which channels,
 * respecting preferences) and doing the sending.
 *
 * @param userId       platform user id, if this recipient is a user. Required
 *                     for IN_APP delivery (that's what the notification bell
 *                     queries by) and for reading their channel preferences.
 *                     Null for external, non-user recipients (email/SMS only).
 * @param displayName  used only in logs/templates, never required to be non-null.
 * @param email        required if EMAIL delivery is attempted for this recipient.
 * @param phone        required if SMS delivery is attempted for this recipient,
 *                     in E.164 format (e.g. "+27821234567").
 */
public record Recipient(UUID userId, String displayName, String email, String phone) {

    public static Recipient user(UUID userId, String displayName, String email, String phone) {
        return new Recipient(userId, displayName, email, phone);
    }

    public static Recipient external(String displayName, String email, String phone) {
        return new Recipient(null, displayName, email, phone);
    }

    public boolean isPlatformUser() {
        return userId != null;
    }
}