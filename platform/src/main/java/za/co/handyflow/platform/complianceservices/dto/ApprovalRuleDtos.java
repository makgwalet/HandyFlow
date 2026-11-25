package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ApprovalRuleDtos {

    private ApprovalRuleDtos() {}

    public record RuleRequest(@NotBlank @Size(max = 120) String name, BigDecimal minFees, @Size(max = 60) String serviceCode, String riskRating, UUID clientId,
                              @NotBlank String requiredApprover, @NotBlank @Size(max = 4000) String reason) {}

    public record NoteRequest(@NotBlank @Size(max = 4000) String note) {}

    public record RuleView(UUID id, String name, BigDecimal minFees, String serviceCode, String riskRating, UUID clientId, String clientName, String requiredApprover, String reason,
                           String status, UUID proposedBy, Instant proposedAt, UUID activatedBy, Instant activatedAt, UUID retiredBy, Instant retiredAt, String retireNote) {}
}
