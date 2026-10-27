package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Which terms a piece of work at a given client site, of a given category, on a given date would be raised under.
 * {@code coverage} is INCLUDED (covered by a retainer — never billed separately) or CHARGEABLE. When no agreement
 * covers the work, {@code agreementFound} is false and the work is CHARGEABLE with no rate card and no SLA.
 */
public record FmResolvedTermsResponse(
        boolean agreementFound, UUID agreementId, String agreementName, String billingType, String coverage,
        BigDecimal labourRate, BigDecimal partsMarkupPercent, List<FmSlaTargetDto> slaTargets
) {}