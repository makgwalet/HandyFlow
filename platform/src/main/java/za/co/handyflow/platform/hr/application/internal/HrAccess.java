package za.co.handyflow.platform.hr.application.internal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a person may see on an employee record. Three layers: the ordinary working details; personal and equity data
 * (ID number, tax number, date of birth, gender, race, emergency contact); and pay (salary, allowances, bank).
 * Used by the controller to redact, and in {@code @PreAuthorize} for the endpoints around them.
 */
public final class HrAccess {

    private HrAccess() {}

    public static boolean seesAll(Set<String> held) {
        return any(held, "HR_READ", "HR_MANAGE", "PAYROLL_RUN", "PAYROLL_PREPARE", "PAYROLL_APPROVE", "USER_READ");
    }

    /** Only their own team: a manager who was given HR_TEAM_READ and nothing wider. */
    public static boolean teamOnly(Set<String> held) { return !seesAll(held) && any(held, "HR_TEAM_READ"); }

    public static boolean sensitive(Set<String> held) { return any(held, "HR_SENSITIVE_READ"); }

    public static boolean compensation(Set<String> held) {
        return any(held, "HR_COMPENSATION_READ", "HR_COMPENSATION_MANAGE", "HR_COMPENSATION_APPROVE");
    }

    public static boolean canAskForPayChange(Set<String> held) { return any(held, "HR_COMPENSATION_MANAGE"); }
    public static boolean canDecidePayChange(Set<String> held) { return any(held, "HR_COMPENSATION_APPROVE"); }

    public static Set<String> held(Authentication a) {
        return a == null ? Set.of() : a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static boolean any(Set<String> held, String... wanted) {
        for (String w : wanted) if (held.contains(w)) return true;
        return false;
    }
}
