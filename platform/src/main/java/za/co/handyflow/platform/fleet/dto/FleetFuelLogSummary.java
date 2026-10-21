package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;

/** Totals over everything the fuel log filter matches (not just the visible page). */
public record FleetFuelLogSummary(long fillups, BigDecimal litres, BigDecimal spend, BigDecimal avgPricePerLitre) {}
