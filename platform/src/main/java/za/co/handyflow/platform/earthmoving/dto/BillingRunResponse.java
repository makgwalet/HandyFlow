package za.co.handyflow.platform.earthmoving.dto;

import java.util.List;

/** What a month's billing found, or did. {@code invoices} are DRAFT invoices; nothing is issued. */
public record BillingRunResponse(String month, boolean applied, List<BillingLine> lines, int ready, int alreadyBilled, int exceptions,
                                 int chargesCreated, List<DraftInvoiceResponse> invoices) {}
