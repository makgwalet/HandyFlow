package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PayRunPaymentDtos {
    private PayRunPaymentDtos() {}

    public record ActionRequest(String note) {}
    public record ReturnRequest(String reason) {}

    public record Skipped(UUID employeeId, String employeeNumber, String employeeName, BigDecimal amount, String reason) {}

    public record Payment(UUID id, UUID employeeId, String employeeNumber, String beneficiaryName, String bankName, String branchCode,
                          String accountMasked, BigDecimal amount, String reference, String status, boolean downloaded, Instant paidAt,
                          Instant returnedAt, String returnReason, boolean canReturn) {}

    public record Summary(UUID payRunId, String payRunNumber, String runStatus, int count, BigDecimal total, int pending, int paid, int returned,
                          BigDecimal returnedTotal, boolean downloaded, boolean canMakeUp, List<Skipped> skipped, List<Payment> payments) {}

    public record FileResult(String filename, String csv, String layout) {
        public FileResult(String filename, String csv) { this(filename, csv, "GENERIC_CSV"); }
    }
}
