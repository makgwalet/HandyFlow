package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DebtorAccountResponse(
        UUID id, UUID clientId, UUID placementBatchId, String accountReference, String debtorName,
        String debtorIdNumber, String debtorEmail, String debtorPhone, String debtorAddress,
        String originalCreditorName, LocalDate originalDebtDate, BigDecimal originalDebtAmount,
        BigDecimal currentBalance, String status, UUID assignedCollectorId, LocalDate placedDate,
        LocalDate closedDate, String notes
) {}
