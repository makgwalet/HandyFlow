package za.co.handyflow.platform.security.application.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starting points for Security roles. A template is not a role: roles belong to a tenant, so the tenant creates a role
 * through the normal role screen and picks a template as its starting set of permissions, then adjusts it.
 *
 * Guards are not a tenant role: they sign in with their own guard login (SECURITY_GUARD is implied by that session).
 * Still under SECURITY_MANAGE (so not yet separate templates): guard create/edit, shifts, incidents, patrols.
 */
public final class SecurityRoleTemplates {

    private SecurityRoleTemplates() {}

    public record Template(String key, String name, String description, Set<String> permissions) {}

    /** Permission pairs that one person should not hold together (checked when a role or assignment is made). */
    public record Conflict(String a, String b, String why) {}

    private static final List<Conflict> CONFLICTS = List.of(
            new Conflict("SECURITY_PAYROLL_APPROVE", "SECURITY_PAYROLL_PAY",
                    "the person who approves a payroll period should not also release payment"),
            new Conflict("SECURITY_PAYROLL_MANAGE", "SECURITY_PAYROLL_APPROVE",
                    "the person who prepares a payroll period should not also approve it"));

    private static final Map<String, Template> ALL = new LinkedHashMap<>();

    private static void add(String key, String name, String description, String... perms) {
        ALL.put(key, new Template(key, name, description, Set.copyOf(List.of(perms))));
    }

    static {
        add("SECURITY_VIEWER", "Security viewer",
                "Read-only access to guards, sites, shifts, incidents and the armoury register. No payroll.",
                "SECURITY_READ");
        add("CONTROL_ROOM_OPERATOR", "Control room operator",
                "Runs day-to-day operations: guards, shifts, incidents, patrols, armoury records. No firearm issue, screening decisions, bank details, payroll or integrations.",
                "SECURITY_READ", "SECURITY_MANAGE", "SECURITY_ALERTS_RECEIVE");
        add("FIELD_SUPERVISOR", "Field supervisor",
                "Operations plus issuing and taking back firearms. No screening decisions, bank details, payroll or integrations.",
                "SECURITY_READ", "SECURITY_MANAGE", "SECURITY_ARMOURY_ISSUE", "SECURITY_ALERTS_RECEIVE");
        add("ARMOURER", "Armourer",
                "Issues and takes back firearms and ammunition; read-only elsewhere.",
                "SECURITY_READ", "SECURITY_ARMOURY_ISSUE");
        add("SCREENING_OFFICER", "Screening / compliance officer",
                "Makes the clear/reject decision on guard screenings; read-only elsewhere.",
                "SECURITY_READ", "SECURITY_SCREENING_DECIDE");
        add("VIP_DETAIL_READER", "VIP detail reader",
                "Read-only access plus VIP protection details.",
                "SECURITY_READ", "VIP_DETAIL_ACCESS");
        add("PAYROLL_PREPARER", "Guard payroll preparer",
                "Creates payroll periods and sets grade rates. Cannot approve or pay.",
                "SECURITY_READ", "SECURITY_PAYROLL_READ", "SECURITY_PAYROLL_MANAGE");
        add("PAYROLL_APPROVER", "Guard payroll approver",
                "Approves payroll periods prepared by someone else. Cannot prepare or pay.",
                "SECURITY_READ", "SECURITY_PAYROLL_READ", "SECURITY_PAYROLL_APPROVE");
        add("PAYROLL_PAYER", "Guard payroll payer",
                "Marks approved payroll periods as paid. Cannot prepare or approve.",
                "SECURITY_READ", "SECURITY_PAYROLL_READ", "SECURITY_PAYROLL_PAY");
        add("INTEGRATIONS_ADMIN", "Security integrations admin",
                "Manages public API keys and webhooks.",
                "SECURITY_READ", "SECURITY_INTEGRATIONS_MANAGE");
        add("SECURITY_ADMIN", "Security administrator",
                "Operations plus module administration and integrations. Sees payroll but cannot prepare, approve or pay it.",
                "SECURITY_READ", "SECURITY_MANAGE", "SECURITY_ADMIN", "SECURITY_PAYROLL_READ",
                "SECURITY_INTEGRATIONS_MANAGE", "SECURITY_ARMOURY_ISSUE", "SECURITY_SCREENING_DECIDE",
                "SECURITY_GUARD_BANKING_MANAGE", "SECURITY_ALERTS_RECEIVE");
    }

    public static Map<String, Template> all() { return Map.copyOf(ALL); }

    public static List<Template> list() { return List.copyOf(ALL.values()); }

    public static List<Conflict> conflicts() { return CONFLICTS; }

    /** The conflicts a set of permissions would create. Empty means fine. */
    public static List<Conflict> conflictsIn(Set<String> permissions) {
        return CONFLICTS.stream()
                .filter(c -> permissions.contains(c.a()) && permissions.contains(c.b()))
                .toList();
    }
}
