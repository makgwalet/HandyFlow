package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.LedgerEntryType;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentSource;
import za.co.handyflow.platform.debtcollection.domain.model.RecoveryEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id, UUID caseId, LedgerEntryType entryType, BigDecimal amount, BigDecimal allocatedPrincipal,
        BigDecimal allocatedFees, LocalDate effectiveDate, PaymentSource source, String externalReference,
        UUID reversalOfId, String feeRuleCode, Integer feeRuleVersion, UUID ptpId, String notes,
        String recordedByUserName, Instant createdAt
) {
    public static LedgerEntryResponse of(RecoveryEntry e) {
        return new LedgerEntryResponse(e.getId(), e.getCaseId(), e.getEntryType(), e.getAmount(), e.getAllocatedPrincipal(),
                e.getAllocatedFees(), e.getEffectiveDate(), e.getSource(), e.getExternalReference(), e.getReversalOfId(),
                e.getFeeRuleCode(), e.getFeeRuleVersion(), e.getPtpId(), e.getNotes(), e.getRecordedByUserName(),
                e.getCreatedAt());
    }
}
