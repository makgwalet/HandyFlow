package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

// RECONSTRUCTED, NOT COPIED FROM SOURCE: same situation as
// BankTransactionResponse — the real file wasn't available, but
// toBankAccountResponse()'s exact constructor call (new
// BankAccountResponse(b.getId(), b.getBankName(), b.getAccountName(),
// b.getAccountNumber(), b.getBranchCode(), b.getAccountType(),
// b.getCurrency(), b.getCurrentBalance(), b.isActive())) fixes the first
// 9 fields' order. accountId is genuinely new — added so the frontend
// can tell whether a bank account has ever been linked to the Chart of
// Accounts at all, which it previously had no way to know.
public record BankAccountResponse(
        UUID       id,
        String     bankName,
        String     accountName,
        String     accountNumber,
        String     branchCode,
        String     accountType,
        String     currency,
        BigDecimal currentBalance,
        boolean    active,
        UUID       accountId,
        BigDecimal lowBalanceThreshold
) {}