package za.co.handyflow.platform.legalpractice.application.internal;

import java.util.*;

import static za.co.handyflow.platform.legalpractice.application.internal.LpPermission.*;

/**
 * Checks a set of authorities for combinations a law firm should avoid. The system already stops one person approving their own request, but a role that holds
 * both sides means one person can do the request and, with a colleague holding the same role, the approval too, and it is a finding in any trust account inspection.
 * {@code LEGALPRACTICE_ADMIN} counts as holding everything.
 */
public final class LpAccessRules {

    private LpAccessRules() { }

    public enum Severity { WARNING, NOTE }

    public record Finding(Severity severity, String code, String message) { }

    private record Pair(LpPermission maker, LpPermission checker, String code, String message) { }

    private static final List<Pair> PAIRS = List.of(
            new Pair(TRUST_PAYMENT_REQUEST, TRUST_PAYMENT_APPROVE, "TRUST_PAYMENT", "The same role can request and approve payments out of trust"),
            new Pair(TRUST_RECONCILE, TRUST_RECONCILE_APPROVE, "TRUST_RECONCILE", "The same role can prepare and approve the trust reconciliation"),
            new Pair(KYC_PREPARE, KYC_APPROVE, "KYC", "The same role can prepare and approve a client's due diligence"),
            new Pair(WALL_MANAGE, WALL_AUDIT, "WALLS", "The same role can put up ethical walls and read the record of who was turned away by them"));

    /** What the role really holds: every permission if it has ADMIN, otherwise exactly what is listed. Unknown codes are ignored here and reported by {@link #unknown}. */
    public static Set<LpPermission> effective(Collection<String> codes) {
        Set<LpPermission> out = EnumSet.noneOf(LpPermission.class);
        for (String c : codes == null ? List.<String>of() : codes) ofCode(c).ifPresent(out::add);
        if (out.contains(ADMIN)) return EnumSet.allOf(LpPermission.class);
        return out;
    }

    public static List<String> unknown(Collection<String> codes) {
        List<String> out = new ArrayList<>();
        for (String c : codes == null ? List.<String>of() : codes) if (c != null && c.startsWith(PREFIX) && ofCode(c).isEmpty()) out.add(c);
        return out;
    }

    public static List<Finding> check(Collection<String> codes) {
        Set<LpPermission> held = effective(codes);
        List<Finding> out = new ArrayList<>();
        boolean admin = codes != null && codes.contains(ADMIN.code());
        if (admin) out.add(new Finding(Severity.WARNING, "ADMIN", "LEGALPRACTICE_ADMIN holds every permission, including both sides of every approval below. Keep it to one or two people"));
        for (Pair p : PAIRS) {
            if (held.contains(p.maker) && held.contains(p.checker)) {
                out.add(new Finding(Severity.WARNING, p.code, admin ? p.message + " (through ADMIN)" : p.message));
            }
        }
        if (held.contains(KYC_APPROVE_SENIOR) && held.contains(KYC_PREPARE)) {
            out.add(new Finding(Severity.WARNING, "KYC_SENIOR", "The same role can prepare due diligence and give the senior approval for it"));
        }
        if (held.contains(TRUST_RECEIVE) && held.contains(TRUST_PAYMENT_APPROVE) && !admin) {
            out.add(new Finding(Severity.NOTE, "TRUST_RECEIVE_APPROVE", "The same role receives money into trust and approves payments out of it"));
        }
        if (held.contains(DEADLINE_RULES) && held.contains(DEADLINE_COMPUTE) && !admin) {
            out.add(new Finding(Severity.NOTE, "DEADLINE_RULES", "The same role changes the deadline rules and computes deadlines with them"));
        }
        if (!held.isEmpty() && !held.contains(READ) && !held.contains(ADMIN) && held.stream().anyMatch(p -> p != REPORT_VIEW && p != WALL_AUDIT)) {
            out.add(new Finding(Severity.NOTE, "NO_READ", "Without LEGALPRACTICE_READ the person cannot open most screens, so the permissions above may not be usable"));
        }
        for (String u : unknown(codes)) out.add(new Finding(Severity.NOTE, "UNKNOWN", u + " is not a Legal Practice permission this system knows"));
        return out;
    }

    private static Optional<LpPermission> ofCode(String c) { return LpPermission.ofCode(c); }
}
