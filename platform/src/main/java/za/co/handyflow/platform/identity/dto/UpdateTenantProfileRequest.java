package za.co.handyflow.platform.identity.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

// FIX (identity module modernization): had zero validation annotations —
// SettingsPage.tsx's "Company" form (name, VAT, phone, address, banking,
// payment terms) could submit unbounded strings with no server-side
// backstop at all, even though the frontend form itself also validates
// nothing before calling Save (see SettingsPage.tsx's own fix). Bounds
// below are generous — wide enough for any real value these fields
// describe — and exist to stop obviously-wrong/oversized input, not to
// second-guess legitimate business data (a very long trading name, an
// unusual VAT format in a different jurisdiction, etc.).
public record UpdateTenantProfileRequest(
        @Size(max = 255, message = "Company name must be 255 characters or fewer")
        String name,
        @Size(max = 30, message = "Phone number must be 30 characters or fewer")
        String phone,
        @Size(max = 30, message = "VAT number must be 30 characters or fewer")
        String vatNumber,
        Map<String, String> address,   // { street, suburb, city, province, postalCode }
        @Size(max = 100, message = "Bank name must be 100 characters or fewer")
        String bankName,
        @Size(max = 50, message = "Bank account must be 50 characters or fewer")
        String bankAccount,
        @Size(max = 20, message = "Branch code must be 20 characters or fewer")
        String bankBranch,
        @Size(max = 1000, message = "Payment terms must be 1000 characters or fewer")
        String paymentTerms
) {}
