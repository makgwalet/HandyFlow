package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;

/** @param legacyAccessEnds last day the permissions payroll used to rely on still work for reading and preparing */
public record PayrollSettingsResponse(boolean allowSelfApproval, Instant updatedAt, LocalDate legacyAccessEnds, boolean legacyAccessOpen,
                                      boolean bankFileSecondCheck, String nominatedBranch, boolean nominatedAccountSet, String nominatedAccountLast4, String taxMethod, boolean etiEnabled) {
    public PayrollSettingsResponse(boolean allowSelfApproval, Instant updatedAt, LocalDate legacyAccessEnds, boolean legacyAccessOpen,
                                   boolean bankFileSecondCheck, String nominatedBranch, boolean nominatedAccountSet, String nominatedAccountLast4, String taxMethod) {
        this(allowSelfApproval, updatedAt, legacyAccessEnds, legacyAccessOpen, bankFileSecondCheck, nominatedBranch, nominatedAccountSet, nominatedAccountLast4, taxMethod, false);
    }
    public PayrollSettingsResponse(boolean allowSelfApproval, Instant updatedAt, LocalDate legacyAccessEnds, boolean legacyAccessOpen,
                                   boolean bankFileSecondCheck, String nominatedBranch, boolean nominatedAccountSet, String nominatedAccountLast4) {
        this(allowSelfApproval, updatedAt, legacyAccessEnds, legacyAccessOpen, bankFileSecondCheck, nominatedBranch, nominatedAccountSet, nominatedAccountLast4, "PERIODIC", false);
    }
    public PayrollSettingsResponse(boolean allowSelfApproval, Instant updatedAt, LocalDate legacyAccessEnds, boolean legacyAccessOpen) {
        this(allowSelfApproval, updatedAt, legacyAccessEnds, legacyAccessOpen, false, null, false, null, "PERIODIC", false);
    }
}
