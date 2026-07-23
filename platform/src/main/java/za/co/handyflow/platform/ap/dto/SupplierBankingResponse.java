package za.co.handyflow.platform.ap.dto;

import java.time.Instant;
import java.util.UUID;

public record SupplierBankingResponse(
        UUID    id,
        String  supplierName,
        String  bankName,
        String  accountHolder,
        String  accountNumber,
        String  branchCode,
        String  vatNumber,
        String  email,
        String  notes,
        Instant createdAt
) {}