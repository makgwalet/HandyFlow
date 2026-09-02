package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cost-per-hectare rollup for one crop cycle. {@code totalLaborHours} is
 * reported as hours, not converted to a currency cost — this module has no
 * stored labor rate anywhere (no payroll/HR-rate integration exists for
 * Agriculture), so summing hours is as far as this report can responsibly
 * go without inventing a business rule. {@code totalYieldHarvested} is a
 * production-efficiency figure, not a cost one — kept alongside cost-per-
 * hectare because the two together (what it cost, what it produced) are
 * what "cost-per-hectare" actually needs to mean something on their own,
 * without pulling in {@code invoicing}'s revenue data (out of this
 * module's scope — see package-info.java).
 */
public record CropCycleCostSummaryResponse(
        UUID cropCycleId,
        String cycleName,
        UUID farmId,
        UUID cropTypeId,
        BigDecimal areaPlantedHectares,
        BigDecimal totalSeedCost,
        BigDecimal totalInputCost,
        BigDecimal totalCost, // seed + input cost — excludes unconverted labor hours, see class Javadoc
        BigDecimal costPerHectare, // null if areaPlantedHectares is 0
        BigDecimal totalLaborHours,
        BigDecimal totalYieldHarvested,
        String yieldUnitOfMeasure, // the crop type's own default unit — see AgCostReportingService
        BigDecimal yieldPerHectare // null if areaPlantedHectares is 0
) {}
