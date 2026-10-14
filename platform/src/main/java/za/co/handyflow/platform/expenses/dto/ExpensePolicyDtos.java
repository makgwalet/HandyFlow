package za.co.handyflow.platform.expenses.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Policies, reimbursement batches and what is owed to staff (finance F18). */
public final class ExpensePolicyDtos {

    private ExpensePolicyDtos() {}

    public record PolicyRequest(String category, BigDecimal maxPerClaim, String limitAction, BigDecimal receiptAbove, Integer maxAgeDays) {}

    public record PolicyResponse(UUID id, String category, BigDecimal maxPerClaim, String limitAction, BigDecimal receiptAbove, Integer maxAgeDays) {}

    public record OwedClaim(UUID claimId, String claimNumber, LocalDate claimDate, String category, BigDecimal amount, boolean inBatch) {}

    public record OwedToEmployee(String employeeName, BigDecimal total, List<OwedClaim> claims) {}

    public record CreateBatchRequest(List<UUID> claimIds) {}

    public record PayBatchRequest(UUID bankAccountId, String reference) {}

    public record BatchItem(UUID claimId, String claimNumber, String employeeName, BigDecimal amount) {}

    public record BatchResponse(UUID id, String batchNumber, String status, BigDecimal totalAmount, UUID bankAccountId, String reference,
                                Instant createdAt, Instant paidAt, List<BatchItem> items) {}
}
