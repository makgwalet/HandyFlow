package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param bankFileSecondCheck null leaves it as it is
 * @param taxMethod          PERIODIC or AVERAGING; null leaves it as it is
 * @param etiEnabled         whether the business claims the employment tax incentive; null leaves it as it is
 * @param nominatedBranch     the company's own branch code for the bank file; null leaves it as it is, blank clears it
 * @param nominatedAccount    the company's own account number for the bank file; null leaves it as it is, blank clears it
 */
public record PayrollSettingsRequest(@NotNull(message = "Say whether one person may prepare and approve") Boolean allowSelfApproval,
                                     Boolean bankFileSecondCheck, String nominatedBranch, String nominatedAccount, String taxMethod, Boolean etiEnabled) {
    public PayrollSettingsRequest(Boolean allowSelfApproval) { this(allowSelfApproval, null, null, null, null, null); }
    public PayrollSettingsRequest(Boolean allowSelfApproval, Boolean bankFileSecondCheck, String nominatedBranch, String nominatedAccount) {
        this(allowSelfApproval, bankFileSecondCheck, nominatedBranch, nominatedAccount, null, null);
    }
    public PayrollSettingsRequest(Boolean allowSelfApproval, Boolean bankFileSecondCheck, String nominatedBranch, String nominatedAccount, String taxMethod) {
        this(allowSelfApproval, bankFileSecondCheck, nominatedBranch, nominatedAccount, taxMethod, null);
    }
}
