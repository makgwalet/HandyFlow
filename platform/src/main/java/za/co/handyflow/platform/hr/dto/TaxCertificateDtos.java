package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for tax certificates and the reconciliation. */
public final class TaxCertificateDtos {

    private TaxCertificateDtos() {}

    public record Settings(String payeReference, String sdlReference, String uifReference, String retirementFundType) {}

    public record Problem(String severity, String subject, String message) {}

    public record Line(String code, String description, BigDecimal amount) {}

    public record Recon(String tax, BigDecimal payslips, BigDecimal certificates, BigDecimal emp201, BigDecimal difference, boolean balanced) {}

    /**
     * A certificate: either one that would be issued (id null) or one that was. {@code state} for a draft is NEW (nothing issued
     * yet), UNCHANGED (the issued one has the same figures) or CHANGED (the figures differ from the issued one).
     */
    public record Certificate(UUID id, int taxYear, String period, UUID employeeId, String employeeNumber, String employeeName,
                              String idNumber, String taxNumber, String certificateNumber, String certificateType, int version,
                              String status, String state, LocalDate periodFrom, LocalDate periodTo, int payslipCount,
                              Instant issuedAt, Instant cancelledAt, String cancelReason, Instant releasedAt,
                              List<Line> lines, List<Problem> problems) {}

    public record Reconciliation(int taxYear, String period, LocalDate periodFrom, LocalDate periodTo, int runs, boolean canIssue,
                                 boolean interim, List<Problem> problems, List<Recon> reconciliation,
                                 List<Certificate> certificates, List<String> notProduced) {}

    public record IssueRequest(Integer taxYear, String period, String reason) {}

    public record ReleaseRequest(Integer taxYear) {}

    public record ReasonRequest(String reason) {}

    public record IssueResult(int issued, int unchanged, int replaced) {}
}
