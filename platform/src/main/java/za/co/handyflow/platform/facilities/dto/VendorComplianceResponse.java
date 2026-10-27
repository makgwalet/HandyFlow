package za.co.handyflow.platform.facilities.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code eligible}: the vendor can be assigned work now (approved and COMPLIANT or EXPIRING_SOON).
 * {@code problems} says why not.
 */
public record VendorComplianceResponse(
        UUID vendorId, String onboardingStatus, String complianceStatus, boolean eligible, List<String> problems,
        List<String> missing, List<String> lapsed, List<Expiring> expiring, List<VendorDocumentResponse> documents
) {
    public record Expiring(String docType, LocalDate expiryDate, long daysLeft) {}
}
