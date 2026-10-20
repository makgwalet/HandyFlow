package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BatteryDetailResponse(
        BatteryResponse summary,
        String supplier,
        LocalDate purchaseDate,
        BigDecimal purchasePrice,
        Long daysToWarrantyExpiry,       // negative once expired; null if no warranty on file
        boolean warrantyClaimable,       // FAULTY and still inside warranty
        BigDecimal costPerMonth,         // purchase price over months in service; null until it has been in service
        String notes,
        Instant scrappedAt,
        String scrapReason,
        List<BatteryInstallationResponse> installations,   // newest first
        List<BatteryTestResponse> tests                    // latest 50, newest first
) {}