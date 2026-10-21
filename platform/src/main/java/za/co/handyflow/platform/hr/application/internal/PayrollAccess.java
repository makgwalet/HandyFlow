package za.co.handyflow.platform.hr.application.internal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * Who may do what in payroll, for use in {@code @PreAuthorize("T(...PayrollAccess).canPrepare(authentication)")}.
 * <p>
 * The dedicated PAYROLL_* permissions are the rule. Until {@link #LEGACY_ACCESS_ENDS} the permissions payroll used to rely
 * on (PAYROLL_RUN and USER_UPDATE) are still accepted for reading and preparing, so a role the migration report
 * flagged does not lose access overnight. After that date they are ignored. Approving and finalising never accepted a legacy
 * permission, because they did not exist.
 */
public final class PayrollAccess {

    /** Last day the old permissions still work for payroll. After it, only PAYROLL_* count. */
    public static final LocalDate LEGACY_ACCESS_ENDS = LocalDate.of(2026, 12, 31);

    private PayrollAccess() {}

    public static boolean canRead(Authentication a)     { return canRead(names(a), LocalDate.now()); }
    public static boolean canPrepare(Authentication a)  { return canPrepare(names(a), LocalDate.now()); }
    public static boolean canApprove(Authentication a)  { return canApprove(names(a)); }
    public static boolean canFinalise(Authentication a) { return canFinalise(names(a)); }
    public static boolean canExport(Authentication a)   { return canExport(names(a), LocalDate.now()); }
    public static boolean canConfigure(Authentication a){ return canConfigure(names(a)); }
    /** Prepare, approve or finalise: anyone who works on pay runs may see the review and the history. */
    public static boolean canReview(Authentication a)   { Set<String> n = names(a); return canRead(n, LocalDate.now()) || canApprove(n) || canFinalise(n); }

    public static boolean canRead(Set<String> held, LocalDate today) {
        return any(held, "PAYROLL_READ", "PAYROLL_PREPARE", "PAYROLL_APPROVE", "PAYROLL_FINALISE")
                || (legacyOpen(today) && any(held, "PAYROLL_RUN", "USER_UPDATE"));
    }

    public static boolean canPrepare(Set<String> held, LocalDate today) {
        return any(held, "PAYROLL_PREPARE") || (legacyOpen(today) && any(held, "PAYROLL_RUN", "USER_UPDATE"));
    }

    public static boolean canApprove(Set<String> held)  { return any(held, "PAYROLL_APPROVE"); }
    public static boolean canFinalise(Set<String> held) { return any(held, "PAYROLL_FINALISE"); }
    public static boolean canConfigure(Set<String> held){ return any(held, "PAYROLL_CONFIGURATION_ADMIN"); }

    public static boolean canExport(Set<String> held, LocalDate today) {
        return any(held, "PAYROLL_STATUTORY_EXPORT") || (legacyOpen(today) && any(held, "PAYROLL_READ", "PAYROLL_RUN"));
    }

    public static boolean legacyOpen(LocalDate today) { return !today.isAfter(LEGACY_ACCESS_ENDS); }

    private static boolean any(Set<String> held, String... wanted) {
        for (String w : wanted) if (held.contains(w)) return true;
        return false;
    }

    private static Set<String> names(Authentication a) {
        if (a == null) return Set.of();
        Collection<? extends GrantedAuthority> auth = a.getAuthorities();
        return auth.stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
    }
}
