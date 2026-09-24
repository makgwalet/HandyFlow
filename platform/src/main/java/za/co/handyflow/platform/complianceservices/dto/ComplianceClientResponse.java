package za.co.handyflow.platform.complianceservices.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * crmCustomerName/crmCustomerFound are looked up LIVE from CrmFacade at
 * read time — never stored, same reference-not-copy pattern
 * TenderPersonnelResponse already established for HR employee references.
 */
public record ComplianceClientResponse(
        UUID id, String name, UUID crmCustomerId, boolean crmCustomerFound, String crmCustomerName,
        String contactEmail, String contactPhone, String mandateNotes, String status, Instant createdAt
) {}
