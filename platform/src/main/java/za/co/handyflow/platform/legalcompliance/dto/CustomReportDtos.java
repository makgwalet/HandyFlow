package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.application.internal.CustomReportRules.Filter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomReportDtos {

    private CustomReportDtos() {}

    public record SaveRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description, @NotBlank @Size(max = 60) String baseKey,
                              @NotNull @Size(min = 1, max = 30) List<String> columns, @Size(max = 10) List<Filter> filters, String sortColumn, boolean sortDesc, boolean shared) {}

    /** mine: the caller owns it. canEdit: the caller may change or delete it (owner, or an administrator). */
    public record View(UUID id, String name, String description, String baseKey, String baseTitle, List<String> columns, List<Filter> filters, String sortColumn, boolean sortDesc,
                       boolean shared, String ownerName, boolean mine, boolean canEdit, Instant updatedAt) {}
}
