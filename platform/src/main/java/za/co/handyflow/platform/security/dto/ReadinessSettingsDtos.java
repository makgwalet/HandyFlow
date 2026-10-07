// security/dto/ReadinessSettingsDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Shapes for the tenant's deployment-readiness requirements. */
public final class ReadinessSettingsDtos {

    private ReadinessSettingsDtos() {}

    public record Option(String value, String label) {}

    public record ReadinessSettingsDto(
            List<String> requiredScreening, List<String> requiredDocuments,
            List<Option> screeningOptions, List<Option> documentOptions,
            boolean customised, String updatedByName, Instant updatedAt) {}

    public record SaveReadinessSettingsRequest(@NotNull List<String> requiredScreening, @NotNull List<String> requiredDocuments) {}
}
