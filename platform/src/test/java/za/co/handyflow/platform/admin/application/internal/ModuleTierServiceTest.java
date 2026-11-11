package za.co.handyflow.platform.admin.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.admin.application.internal.TierPricing.Period;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModuleTierServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ModuleTierService service = new ModuleTierService(jdbc);
    private final UUID tenant = UUID.randomUUID();

    @Test
    void approvingNeedsAKnownAdministrator() {
        assertThrows(HandyFlowException.class, () -> service.approvePrice(UUID.randomUUID(), null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void approvingAnAlreadyApprovedPriceIsRejected() {
        when(jdbc.update(contains("SET approved = TRUE"), any(Object[].class))).thenReturn(0);
        assertThrows(HandyFlowException.class, () -> service.approvePrice(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void anApprovedPriceCannotBeWithdrawn() {
        when(jdbc.update(contains("DELETE FROM module_tier_prices"), any(Object[].class))).thenReturn(0);
        assertThrows(HandyFlowException.class, () -> service.withdrawPrice(UUID.randomUUID()));
    }

    @Test
    void unknownTierIsRejectedWhenProposingAPrice() {
        when(jdbc.query(contains("FROM module_tiers"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        assertThrows(HandyFlowException.class, () -> service.proposePrice("warehousing", "NOPE", Period.MONTHLY,
                new BigDecimal("1"), LocalDate.parse("2026-01-01"), UUID.randomUUID(), null));
    }

    @Test
    void negativePriceIsRejected() {
        when(jdbc.query(contains("FROM module_tiers"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(1));
        assertThrows(HandyFlowException.class, () -> service.proposePrice("warehousing", "SME", Period.MONTHLY,
                new BigDecimal("-1"), LocalDate.parse("2026-01-01"), UUID.randomUUID(), null));
    }

    private Map<String, Object> sub(String period, String from, String price) {
        return Map.of("id", UUID.randomUUID(), "module_key", "warehousing", "tier_code", "SME", "tier_name", "SME",
                "billing_period", period, "effective_from", Date.valueOf(from), "price_snapshot", new BigDecimal(price));
    }

    @Test
    void monthlyPricedSubscriptionChargesItsSnapshot() {
        when(jdbc.queryForList(contains("s.price_snapshot IS NOT NULL"), any(Object[].class)))
                .thenReturn(List.of(sub("MONTHLY", "2026-01-01", "100.00")));
        when(jdbc.queryForList(contains("proration_amount IS NOT NULL"), any(Object[].class))).thenReturn(List.of());
        var charges = service.chargesFor(tenant, YearMonth.of(2026, 7));
        assertEquals(new BigDecimal("100.00"), charges.get("warehousing").amount());
        assertFalse(charges.get("warehousing").prepaid());
    }

    @Test
    void annualSubscriptionIsPrepaidBetweenAnniversaries() {
        when(jdbc.queryForList(contains("s.price_snapshot IS NOT NULL"), any(Object[].class)))
                .thenReturn(List.of(sub("ANNUAL", "2026-03-01", "1000.00")));
        when(jdbc.queryForList(contains("proration_amount IS NOT NULL"), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForList(contains("ORDER BY effective_from DESC"), any(Object[].class)))
                .thenReturn(List.of(Map.<String, Object>of("billing_period", "ANNUAL", "effective_from", Date.valueOf("2026-03-01"))));
        var june = service.chargesFor(tenant, YearMonth.of(2026, 6)).get("warehousing");
        assertEquals(0, june.amount().signum());
        assertTrue(june.prepaid());
        var nextMarch = service.chargesFor(tenant, YearMonth.of(2027, 3)).get("warehousing");
        assertEquals(new BigDecimal("1000.00"), nextMarch.amount());
    }

    @Test
    void pendingUpgradeAdjustmentIsAttachedToTheModuleCharge() {
        UUID adjId = UUID.randomUUID();
        when(jdbc.queryForList(contains("s.price_snapshot IS NOT NULL"), any(Object[].class)))
                .thenReturn(List.of(sub("MONTHLY", "2026-07-17", "200.00")));
        when(jdbc.queryForList(contains("proration_amount IS NOT NULL"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", adjId, "module_key", "warehousing", "proration_amount", new BigDecimal("48.39"))));
        var c = service.chargesFor(tenant, YearMonth.of(2026, 8)).get("warehousing");
        assertEquals(new BigDecimal("48.39"), c.proration());
        assertEquals(adjId, c.prorationSubscriptionId());
    }
}
