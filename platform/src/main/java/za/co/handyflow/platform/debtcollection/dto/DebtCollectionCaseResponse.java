package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.ClosureReason;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record DebtCollectionCaseResponse(
        UUID id,
        String caseNumber,
        UUID customerId,
        String debtorName,
        String debtorEmail,
        String debtorPhone,
        CaseStatus status,
        BigDecimal totalOutstanding,
        Set<UUID> linkedInvoiceIds,
        LocalDate openedDate,
        LocalDate closedDate,
        ClosureReason closureReason,
        UUID assignedToUserId,
        String assignedToUserName,
        UUID linkedContractId,
        LocalDate lastContactDate,
        LocalDate nextActionDate,
        BigDecimal writeOffAmount,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static DebtCollectionCaseResponse of(DebtCollectionCase c) {
        return new DebtCollectionCaseResponse(
                c.getId(), c.getCaseNumber(), c.getCustomerId(), c.getDebtorName(), c.getDebtorEmail(),
                c.getDebtorPhone(), c.getStatus(), c.getTotalOutstanding(), c.getLinkedInvoiceIds(),
                c.getOpenedDate(), c.getClosedDate(), c.getClosureReason(), c.getAssignedToUserId(),
                c.getAssignedToUserName(), c.getLinkedContractId(), c.getLastContactDate(), c.getNextActionDate(),
                c.getWriteOffAmount(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
