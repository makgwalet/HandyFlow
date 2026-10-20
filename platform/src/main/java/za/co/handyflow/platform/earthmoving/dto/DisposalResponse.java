package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisposalResponse(UUID id, UUID assetId, String assetName, String fleetNumber, String reason, String status, String details,
                               String counterparty, BigDecimal proceeds, UUID requestedBy, Instant requestedAt, UUID decidedBy, Instant decidedAt,
                               String decisionNote, UUID completedBy, Instant completedAt, UUID cancelledBy, Instant cancelledAt, String cancelReason,
                               long evidenceCount, List<String> blockers) {}
