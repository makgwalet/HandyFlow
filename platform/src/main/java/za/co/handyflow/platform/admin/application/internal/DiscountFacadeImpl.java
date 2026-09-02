// admin/application/internal/DiscountFacadeImpl.java

package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.admin.DiscountFacade;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DiscountFacadeImpl — thin adapter over the existing, unmodified
 * AdminDiscountService. Delegates entirely; no discount logic lives
 * here, matching this session's own explicit instruction not to touch
 * resolveDiscount()'s resolution logic.
 */
@Service
@RequiredArgsConstructor
public class DiscountFacadeImpl implements DiscountFacade {

    private final AdminDiscountService discountService;

    @Override
    public DiscountOutcome resolveAndRecordDiscount(UUID tenantId, String moduleKey,
                                                    String discountCode, BigDecimal originalPrice,
                                                    UUID activatedBy) {
        var result = discountService.resolveDiscount(tenantId, moduleKey, discountCode);
        if (result.pct().compareTo(BigDecimal.ZERO) > 0) {
            discountService.applyAndRecord(tenantId, moduleKey, originalPrice, result, activatedBy);
        }
        return new DiscountOutcome(result.pct(), result.source());
    }
}