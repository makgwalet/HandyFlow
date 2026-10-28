package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;

/** @param legacyAccessEnds last day the permissions payroll used to rely on still work for reading and preparing */
public record PayrollSettingsResponse(boolean allowSelfApproval, Instant updatedAt, LocalDate legacyAccessEnds, boolean legacyAccessOpen,
                                      boolean bankFileSecondCheck, String nominatedBranch, boolean nominatedAccountSet, String nominatedAccountLast4) {
    public PayrollSettingsResponse(boolean allowSelfApproval, Instant updatedAt, LocalDate legacyAccessEnds, boolean legacyAccessOpen) {
        this(allowSelfApproval, updatedAt, legacyAccessEnds, legacyAccessOpen, false, null, false, null);
    }
}
