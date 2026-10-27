package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code siteIds} empty/absent = covers every site of the client; {@code categories} empty/absent = every category
 * (PPM, CORRECTIVE, EMERGENCY, INSPECTION, OTHER). {@code hourlyRate} is the default labour charge and
 * {@code partsMarkupPercent} the default parts markup for work raised under this agreement. {@code slaTargets} is optional.
 */
public record CreateFmServiceAgreementRequest(
        @NotNull UUID clientId, String name, String billingType, BigDecimal monthlyFee, BigDecimal hourlyRate,
        BigDecimal partsMarkupPercent, @NotNull LocalDate startDate, LocalDate endDate,
        Set<UUID> siteIds, Set<String> categories, @Valid List<FmSlaTargetDto> slaTargets
) {}