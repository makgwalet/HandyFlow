package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.Size;

/** {@code note} is required to approve an invoice that is billed more than was received, or billed with nothing received. */
public record InvoiceDecisionRequest(@Size(max = 500) String note) {}
