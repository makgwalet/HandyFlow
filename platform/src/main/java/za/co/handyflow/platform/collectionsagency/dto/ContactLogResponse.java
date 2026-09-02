package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContactLogResponse(
        UUID id, UUID debtorAccountId, LocalDate contactDate, String contactMethod, String outcome,
        boolean disclosedThirdPartyCollector, boolean disclosedOriginalCreditor, boolean disclosedDebtorRights,
        String notes, LocalDate promisedPaymentDate, BigDecimal promisedPaymentAmount, UUID recordedByUserId,
        String recordedByUserName
) {}
