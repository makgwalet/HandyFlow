package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProviderDtos {

    private ProviderDtos() {}

    public record ProviderRequest(@NotBlank @Size(max = 200) String name, String kind, @Size(max = 255) String email, @Size(max = 50) String phone, @Size(max = 500) String coverage, @Size(max = 4000) String notes) {}

    public record ReasonRequest(@NotBlank @Size(max = 4000) String reason) {}

    public record ServicesRequest(List<String> serviceCodes) {}

    public record AccreditationRequest(@NotBlank @Size(max = 200) String body, @NotBlank @Size(max = 100) String reference, LocalDate issuedOn, LocalDate expiresOn) {}

    public record CheckRequest(boolean accepted, @NotBlank @Size(max = 4000) String note) {}

    public record NoteRequest(@NotBlank @Size(max = 4000) String note) {}

    /** usable is true only when a different person checked it and it has not expired. */
    public record AccreditationView(UUID id, String body, String reference, LocalDate issuedOn, LocalDate expiresOn, String status, String expiry, boolean usable,
                                    UUID recordedBy, Instant recordedAt, UUID checkedBy, Instant checkedAt, String checkNote, String retireNote) {}

    public record ProviderSummary(UUID id, String name, String kind, String status, String email, String phone, String coverage, List<String> services,
                                  int usableAccreditations, int unchecked, LocalDate nextExpiry) {}

    public record ProviderDetail(ProviderSummary provider, String notes, String inactiveReason, Instant createdAt, List<AccreditationView> accreditations) {}
}
