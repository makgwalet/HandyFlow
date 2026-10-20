package za.co.handyflow.platform.fleet.dto;

import za.co.handyflow.platform.fleet.domain.model.ComplianceStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ComplianceItemResponse(
        String key,             // LICENCE_DISC | ROADWORTHY | INSURANCE | TRACKING | SERVICE
        String label,
        ComplianceStatus status,
        LocalDate expiryDate,   // null for items with no expiry (tracking, service)
        Long daysRemaining,     // negative once expired
        UUID documentId,        // the current vault document backing this item, if any
        String detail
) {}