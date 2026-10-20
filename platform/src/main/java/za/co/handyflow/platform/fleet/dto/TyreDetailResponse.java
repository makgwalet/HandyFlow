package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TyreDetailResponse(
        TyreResponse summary,
        LocalDate purchaseDate,
        BigDecimal purchasePrice,
        String supplier,
        BigDecimal initialTreadDepthMm,
        String notes,
        Instant scrappedAt,
        String scrapReason,
        BigDecimal totalCost,            // purchase price + retread costs
        BigDecimal costPerKm,            // total cost / lifetime km; null until it has run some km
        List<TyreFitmentResponse> fitments,       // newest first
        List<TyreMeasurementResponse> measurements, // latest 50, newest first
        List<TyreRetreadResponse> retreads
) {}