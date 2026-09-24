package za.co.handyflow.platform.shared;

import java.util.Map;
import java.util.UUID;

/**
 * A single, named, audited action a support engineer can trigger against
 * a tenant's data through the admin console — the concrete implementation
 * of the business decision recorded in the strategic roadmap backlog
 * (Part 0, Decision 1): "Allow, but only through a controlled
 * support-action framework," specifically ruling out the alternative that
 * was on the table (widening {@code admin}'s module boundary so it could
 * call any other module's business logic directly).
 * <p>
 * Deliberately lives in {@code shared}, not in {@code admin} — every
 * module already depends on {@code shared}, so a module that wants to
 * expose a support action implements this interface as a normal
 * {@code @Component} bean IN ITS OWN MODULE (e.g.
 * {@code identity.application.internal.ResendVerificationEmailAction}).
 * Spring's {@code List<SupportAction>} collection-injection then gathers
 * every implementation across the whole application context into
 * {@code admin}'s {@code SupportActionService} at runtime — but {@code
 * admin}'s own Java code never imports a single type from {@code
 * identity} or any other business module to do this. Spring Modulith's
 * architecture verification checks static package dependencies, and
 * {@code admin} genuinely has none on the modules whose actions it runs;
 * this is exactly the "published interface" decoupling pattern Modulith
 * itself recommends for this shape of problem, not a workaround.
 * <p>
 * This is a closed list on purpose — {@code SupportActionService} only
 * ever executes an action a module has explicitly registered as a bean
 * of this type. There is no generic "call any method on any service"
 * escape hatch anywhere in this design.
 */
public interface SupportAction {

    /** Stable, unique identifier — e.g. "RESEND_VERIFICATION_EMAIL". Never changes once shipped; used in the audit log and by callers. */
    String key();

    /** Short, human-readable name for the admin console's action list — e.g. "Resend verification email". */
    String label();

    /** One or two sentences explaining what this action does and when a support engineer should use it. */
    String description();

    /**
     * Executes the action. {@code targetId} is the specific record this
     * action acts on within the tenant (a user id for a
     * per-user action, {@code null} for a tenant-wide action) — its
     * meaning is defined by each action, not by this interface.
     * {@code params} carries any additional input a specific action
     * needs; empty for actions that need none.
     * <p>
     * Implementations should catch their own expected failure cases and
     * return a {@code SupportActionResult} describing them, rather than
     * throwing — {@code SupportActionService} audits both outcomes
     * either way, but a clear failure result is more useful in the audit
     * log than a generic "action threw an exception" entry.
     */
    SupportActionResult execute(TenantId tenantId, UUID targetId, Map<String, String> params, UUID performedByAdminId);
}
