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
        String paymentTerms,
        // FIX (identity module modernization): TenantService.updateBillingContact()
        // has always persisted these three fields correctly, but this record
        // never carried them back out — GET /api/v1/identity/tenants/me could
        // never confirm what was actually saved, and the Settings UI had
        // nothing to read to build a billing-contact form against. See
        // TenantController.updateBillingContact()'s own Javadoc, which
        // already flagged this exact gap and pointed back here.
        String billingEmail,
        String billingContactName,
        String billingPhone
) {}