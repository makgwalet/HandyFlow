package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

public record ItemApprovalResponse(UUID id, UUID itemId, String itemName, String sku, String itemStatus, String status, UUID requestedBy, Instant requestedAt,
                                   String note, UUID decidedBy, Instant decidedAt, String decisionNote) {}
