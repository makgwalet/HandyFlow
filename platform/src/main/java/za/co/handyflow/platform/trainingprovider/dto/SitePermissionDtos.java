package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class SitePermissionDtos {
    private SitePermissionDtos() {
    }

    public record PermissionRequest(@NotNull UUID practitionerId, @NotNull UUID siteId, @NotBlank String role, UUID programmeId, @NotNull LocalDate validFrom,
                                    LocalDate validTo, @NotBlank String basis) {
    }

    public record WithdrawRequest(@NotBlank String reason) {
    }

    public record PermissionView(UUID id, UUID practitionerId, String practitionerNo, String practitionerName, UUID siteId, String siteName, String role, UUID programmeId,
                                 String programmeTitle, LocalDate validFrom, LocalDate validTo, String basis, String status, String withdrawnReason, boolean inForceToday,
                                 Instant createdAt) {
    }
}
