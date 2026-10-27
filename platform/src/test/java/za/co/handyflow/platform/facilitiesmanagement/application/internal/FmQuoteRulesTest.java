package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuoteLine;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Quote arithmetic, validity limits and the quote's state machine. */
class FmQuoteRulesTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 10, 27);
    private final UUID user = UUID.randomUUID();

    private FmQuote quote(LocalDate validUntil) {
        FmQuote q = FmQuote.create(tenant, "FMQ-1", UUID.randomUUID(), UUID.randomUUID(), null, null, "Replace pump", "Details", validUntil, "margin 20%", user);
        q.setAmounts(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("0.00"), new BigDecimal("100.00"));
        return q;
    }
    private FmQuoteLine line(String qty, String price) { return FmQuoteLine.create(tenant, UUID.randomUUID(), 1, "x", new BigDecimal(qty), new BigDecimal(price)); }

    @Test
    void vatIsAddedOnTheSubtotalWhenRegistered() {
        FmBillingSettings vat = new FmBillingSettings(true, new BigDecimal("15.00"), 30, "1100", "4000", null, null);
        FmQuoteRules.Totals t = FmQuoteRules.totals(List.of(line("2", "100.00"), line("1.5", "10.00")), vat);
        assertEquals(new BigDecimal("215.00"), t.subtotal());
        assertEquals(new BigDecimal("32.25"), t.vatAmount());
        assertEquals(new BigDecimal("247.25"), t.total());
    }

    @Test
    void noVatWhenNotRegistered() {
        FmQuoteRules.Totals t = FmQuoteRules.totals(List.of(line("1", "100.00")), FmBillingSettings.defaults());
        assertEquals(new BigDecimal("0.00"), t.vatAmount());
        assertEquals(new BigDecimal("100.00"), t.total());
    }

    @Test
    void aTotalAboveTheLimitIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> FmQuoteRules.totals(List.of(line("1", "10000001.00")), FmBillingSettings.defaults()));
    }

    @Test
    void validityMustBeTodayOrLaterAndWithinNinetyDays() {
        assertDoesNotThrow(() -> FmQuoteRules.requireValidity(today, today));
        assertDoesNotThrow(() -> FmQuoteRules.requireValidity(today.plusDays(90), today));
        assertThrows(IllegalArgumentException.class, () -> FmQuoteRules.requireValidity(today.minusDays(1), today));
        assertThrows(IllegalArgumentException.class, () -> FmQuoteRules.requireValidity(today.plusDays(91), today));
    }

    @Test
    void aDraftIsEditableThenFrozenOnceSent() {
        FmQuote q = quote(today.plusDays(10));
        q.edit("New title", null, today.plusDays(5), null);
        assertEquals("New title", q.getTitle());
        q.send(user, today);
        assertEquals("SENT", q.getStatus());
        assertThrows(IllegalStateException.class, () -> q.edit("x", null, today.plusDays(5), null));
        assertThrows(IllegalStateException.class, () -> q.send(user, today));
    }

    @Test
    void aQuoteWithNoValueCannotBeSent() {
        FmQuote q = quote(today.plusDays(10));
        q.setAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> q.send(user, today));
    }

    @Test
    void acceptAndDeclineWorkOnlyWhileOpen() {
        UUID portal = UUID.randomUUID();
        FmQuote a = quote(today.plusDays(10)); a.send(user, today);
        a.accept(portal, " Go ahead ", today);
        assertEquals("ACCEPTED", a.getStatus());
        assertEquals("Go ahead", a.getDecisionNote());
        assertEquals(portal, a.getDecidedByPortalUserId());
        assertThrows(IllegalStateException.class, () -> a.decline(portal, "no", today));

        FmQuote d = quote(today.plusDays(10)); d.send(user, today);
        assertThrows(IllegalArgumentException.class, () -> d.decline(portal, " ", today));
        d.decline(portal, "Too expensive", today);
        assertEquals("DECLINED", d.getStatus());
    }

    @Test
    void theLastDayOfValidityStillCountsButTheNextDoesNot() {
        FmQuote q = quote(today); q.send(user, today);
        assertTrue(q.isOpenForDecision(today));
        assertFalse(q.isOpenForDecision(today.plusDays(1)));
        assertEquals("EXPIRED", FmQuoteRules.effectiveStatus(q, today.plusDays(1)));
        assertEquals("SENT", FmQuoteRules.effectiveStatus(q, today));
        assertThrows(IllegalStateException.class, () -> q.accept(UUID.randomUUID(), null, today.plusDays(1)));
    }

    @Test
    void expireOnlyAppliesToAPastDueSentQuote() {
        FmQuote q = quote(today); q.send(user, today);
        assertThrows(IllegalStateException.class, () -> q.expire(today));
        q.expire(today.plusDays(1));
        assertEquals("EXPIRED", q.getStatus());
        assertThrows(IllegalStateException.class, () -> q.accept(UUID.randomUUID(), null, today));
    }

    @Test
    void withdrawNeedsAReasonAndASentQuote() {
        FmQuote q = quote(today.plusDays(3));
        assertThrows(IllegalStateException.class, () -> q.withdraw("oops"));
        q.send(user, today);
        assertThrows(IllegalArgumentException.class, () -> q.withdraw(" "));
        q.withdraw("Wrong scope");
        assertEquals("WITHDRAWN", q.getStatus());
    }

    @Test
    void lineTotalIsQuantityTimesPriceToTwoDecimals() {
        assertEquals(new BigDecimal("33.33"), line("3.333", "10.00").getLineTotal());
    }
}
