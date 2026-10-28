package za.co.handyflow.platform.hr.dto;

import java.time.LocalDate;

/** accountType: CURRENT, SAVINGS, TRANSMISSION or BOND; blank is current. */
public record BankChangeRequest(String bankName, String accountNumber, String branchCode, LocalDate effectiveFrom, String reason, String accountType) {
    /** Without the account type, as the request was before it was kept. */
    public BankChangeRequest(String bankName, String accountNumber, String branchCode, LocalDate effectiveFrom, String reason) {
        this(bankName, accountNumber, branchCode, effectiveFrom, reason, null);
    }
}
