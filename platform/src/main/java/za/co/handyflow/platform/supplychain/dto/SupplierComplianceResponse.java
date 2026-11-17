package za.co.handyflow.platform.supplychain.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SupplierComplianceResponse(
        UUID supplierId,
        String overall,
        List<Item> items
) {
    public record Item(String docType, String status, LocalDate expiryDate, Long daysToExpiry) {}
}
