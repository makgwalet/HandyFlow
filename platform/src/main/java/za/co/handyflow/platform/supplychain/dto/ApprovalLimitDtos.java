package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for monetary approval limits. A null maxAmount means unlimited. */
public final class ApprovalLimitDtos {

    private ApprovalLimitDtos() {}

    /** subjectType USER | ROLE; documentType * | REQUISITION | PURCHASE_ORDER | RFQ_AWARD | PO_AMENDMENT | SUPPLIER_INVOICE. */
    public record SaveLimitRequest(String subjectType, UUID subjectId, String documentType, BigDecimal maxAmount) {}

    public record ModeRequest(String mode) {}

    public record LimitRow(UUID id, String subjectType, UUID subjectId, String subjectName, String documentType,
                           BigDecimal maxAmount, String createdByName, Instant updatedAt) {}

    /** enforceReady: somebody active holds an unlimited limit over everything, so enforcement can be switched on safely. */
    public record LimitsView(String mode, boolean enforceReady, List<LimitRow> limits) {}

    public record MyAuthority(String documentType, boolean granted, boolean unlimited, BigDecimal maxAmount, String source) {}

    public record MyLimits(String mode, List<MyAuthority> authorities) {}
}
