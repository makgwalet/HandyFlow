package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One change to many items. {@code action} is STATUS (needs status, and a reason for suspend / discontinue / archive),
 * CATEGORY (needs categoryId) or PRICE_PERCENT (needs percent, for example 5 or -10; an optional date and reason).
 * With {@code dryRun} nothing is changed: the response shows what would happen to each item.
 */
public record BulkRequest(@NotEmpty(message = "Choose at least one item") List<UUID> ids, @NotNull(message = "Choose what to change") String action,
                          CatalogueItemStatus status, String reason, UUID categoryId, BigDecimal percent, LocalDate effectiveFrom, boolean dryRun) {}
