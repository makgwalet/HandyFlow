package za.co.handyflow.platform.identity;

import java.util.Map;
import java.util.UUID;

public record TenantDetails(
        UUID id,
        String companyName,
        String slug,
        String vatNumber,
        String phone,
        String email,
        Map<String, String> address,
        String logoUrl,
        String bankName,
        String bankAccount,
        String bankBranch,
        String paymentTerms
) {}