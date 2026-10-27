package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code inForce}: valid today (not revoked, not expired). */
public record FmVendorDocumentResponse(
        UUID id, UUID vendorId, String docType, String reference, String issuedBy, LocalDate issueDate,
        LocalDate expiryDate, BigDecimal coverAmount, String documentRef, String status, boolean inForce,
        String revokedReason, Instant createdAt
) {}
