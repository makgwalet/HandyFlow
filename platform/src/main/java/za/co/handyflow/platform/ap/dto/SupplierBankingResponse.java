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
        Instant createdAt,
        // Finance patch F6: who last changed the details money is paid to, and who checked them.
        boolean verified,
        UUID    changedBy,
        Instant changedAt,
        UUID    verifiedBy,
        Instant verifiedAt,
        String  verificationNote
) {}