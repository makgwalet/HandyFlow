package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotBlank;

/** Run a month's billing. With {@code createDrafts} the charges are also put on draft invoices, one per customer. */
public record BillingRunRequest(@NotBlank String month, boolean createDrafts) {}
