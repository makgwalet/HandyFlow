package za.co.handyflow.platform.identity.dto;

import java.util.Map;

public record UpdateTenantProfileRequest(
        String name,
        String phone,
        String vatNumber,
        Map<String, String> address,   // { street, suburb, city, province, postalCode }
        String bankName,
        String bankAccount,
        String bankBranch,
        String paymentTerms
) {}