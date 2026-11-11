package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** What the event owes and has paid one supplier. vendorId is null for commitments with no supplier attached. */
public record SupplierSummary(
        UUID vendorId,
        String vendorName,
        int commitmentCount,
        BigDecimal committed,
        BigDecimal invoiced,
        BigDecimal paid,
        BigDecimal outstanding
) {}