package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The text of a supplier's price list as CSV. {@code supplier} names the supplier for rows that have no supplier column; {@code defaultCategory} is used for rows without a
 * category. {@code dryRun} reports what would happen without saving anything.
 */
public record ImportTenderRatesRequest(
        @NotBlank @Size(max = 3_000_000) String csv,
        @Size(max = 120) String supplier,
        String defaultCategory,
        boolean dryRun
) {}
