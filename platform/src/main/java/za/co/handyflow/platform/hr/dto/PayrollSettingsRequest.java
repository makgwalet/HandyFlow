package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param bankFileSecondCheck null leaves it as it is
 * @param nominatedBranch     the company's own branch code for the bank file; null leaves it as it is, blank clears it
 * @param nominatedAccount    the company's own account number for the bank file; null leaves it as it is, blank clears it
 */
public record PayrollSettingsRequest(@NotNull(message = "Say whether one person may prepare and approve") Boolean allowSelfApproval,
                                     Boolean bankFileSecondCheck, String nominatedBranch, String nominatedAccount) {
    public PayrollSettingsRequest(Boolean allowSelfApproval) { this(allowSelfApproval, null, null, null); }
}
