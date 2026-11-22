package za.co.handyflow.platform.security.application.internal;

import java.util.Set;

/**
 * What a company's standing means for the three ways machines and outsiders reach the Security module without a
 * logged-in user (guards are covered by {@link TenantAccessRules}). Pure.
 *
 * <ul>
 *   <li>Camera motion webhook: a motion event raises an alarm, which is a safety signal, so it is accepted while
 *       SUSPENDED (same reasoning as the guard panic button) and refused only when CANCELLED.</li>
 *   <li>Client portal (public link): a service the company provides to its client, so it goes dark as soon as the
 *       company is SUSPENDED. The answer is the same "not found" a disabled portal gives, revealing nothing.</li>
 *   <li>API keys: while SUSPENDED a key may still read (the company can export its data) but not change anything;
 *       when CANCELLED it is refused.</li>
 * </ul>
 */
public final class TenantEntryRules {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private TenantEntryRules() {}

    public static boolean motionWebhookAllowed(TenantAccessRules.Access a) {
        return a != TenantAccessRules.Access.BLOCKED;
    }

    public static boolean portalAllowed(TenantAccessRules.Access a) {
        return a == TenantAccessRules.Access.FULL;
    }

    public static boolean apiKeyAllowed(TenantAccessRules.Access a, String method) {
        return switch (a) {
            case FULL -> true;
            case BLOCKED -> false;
            case RESTRICTED -> method != null && SAFE_METHODS.contains(method.toUpperCase());
        };
    }
}
