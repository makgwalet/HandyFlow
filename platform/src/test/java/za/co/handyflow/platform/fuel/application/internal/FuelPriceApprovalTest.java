package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPriceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.fuel.dto.PriceRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** B07: price changes that move too far, or leave too little margin, wait for approval. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPriceApprovalTest {

    @Mock FuelPriceRepository prices;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock CrmFacade crm;
    @Mock FuelSettingsRepository settings;
    @Mock FuelReceiptRepository receipts;
    @Mock ApprovalFacade approvals;
    @InjectMocks FuelPricingService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final LocalDate D = LocalDate.of(2026, 1, 1);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelPrice price(String p, String previous, String cost) {
        FuelPrice fp = FuelPrice.create(T, "DIESEL", null, bd(p), null, D, null, null, null);
        fp.recordContext(previous == null ? null : bd(previous), cost == null ? null : bd(cost));
        return fp;
    }

    private FuelSettings settings(String changePct, String minMargin) {
        FuelSettings s = FuelSettings.forTenant(T);
        s.updatePriceControls(changePct == null ? null : bd(changePct), minMargin == null ? null : bd(minMargin));
        return s;
    }

    /** Makes save() remember what it was given, so list() can find the new price again. */
    private List<FuelPrice> savedPrices() {
        List<FuelPrice> saved = new java.util.ArrayList<>();
        when(prices.save(any(FuelPrice.class))).thenAnswer(i -> { FuelPrice p = i.getArgument(0); if (!saved.contains(p)) saved.add(p); return p; });
        when(prices.findAllForTenant(T)).thenAnswer(i -> new java.util.ArrayList<>(saved));
        return saved;
    }

    private ApprovalRequestResponse response(String status) {
        return new ApprovalRequestResponse(UUID.randomUUID(), "fuel", "PRICE", UUID.randomUUID(), status, null, null, null, null, null, null);
    }

    @Test @DisplayName("change and margin are worked out from what the price replaced and the cost at entry")
    void maths() {
        FuelPrice p = price("22.00", "20.00", "18.00");
        assertEquals(0, bd("10.00").compareTo(p.changePct()));
        assertEquals(0, bd("18.18").compareTo(p.marginPct()));
        assertNull(price("22.00", null, null).changePct());
        assertNull(price("22.00", null, null).marginPct());
    }

    @Test @DisplayName("with no limits set nothing ever needs approval")
    void offByDefault() {
        assertNull(FuelPricingService.approvalReason(settings(null, null), price("40.00", "20.00", "30.00")));
        assertNull(FuelPricingService.approvalReason(null, price("40.00", "20.00", "30.00")));
    }

    @Test @DisplayName("a move over the limit needs approval, in either direction; at the limit it does not")
    void changeLimit() {
        assertNotNull(FuelPricingService.approvalReason(settings("5", null), price("21.20", "20.00", null)));
        assertNotNull(FuelPricingService.approvalReason(settings("5", null), price("18.80", "20.00", null)));
        assertNull(FuelPricingService.approvalReason(settings("5", null), price("21.00", "20.00", null)));
    }

    @Test @DisplayName("a margin under the minimum needs approval; unknown cost never blocks")
    void marginFloor() {
        assertNotNull(FuelPricingService.approvalReason(settings(null, "10"), price("20.00", null, "19.00")));
        assertNull(FuelPricingService.approvalReason(settings(null, "10"), price("22.00", null, "19.00")));
        assertNull(FuelPricingService.approvalReason(settings(null, "10"), price("20.00", null, null)));
    }

    @Test @DisplayName("zero switches a limit off; a null leaves it alone; bad values are refused")
    void settingsSemantics() {
        FuelSettings s = settings("5", "10");
        s.updatePriceControls(null, null);
        assertEquals(0, bd("5").compareTo(s.getPriceChangeApprovalPct()));
        s.updatePriceControls(BigDecimal.ZERO, null);
        assertNull(s.getPriceChangeApprovalPct());
        assertEquals(0, bd("10").compareTo(s.getMinMarginPct()));
        assertThrows(IllegalArgumentException.class, () -> s.updatePriceControls(bd("-1"), null));
        assertThrows(IllegalArgumentException.class, () -> s.updatePriceControls(null, bd("100")));
    }

    @Test @DisplayName("a pending price does not apply, ending it is refused, and the decision applies once")
    void lifecycle() {
        FuelPrice p = price("22.00", "20.00", null);
        p.submitForApproval(UUID.randomUUID(), "Moves 10%");
        assertTrue(p.isPending());
        assertFalse(p.isLive(D.plusDays(5)));
        assertThrows(IllegalStateException.class, () -> p.end(D.plusDays(10)));
        assertTrue(p.approvalGranted());
        assertTrue(p.isLive(D.plusDays(5)));
        assertFalse(p.approvalGranted());
        assertFalse(p.approvalRejected());
    }

    @Test @DisplayName("a rejected price never applies")
    void rejected() {
        FuelPrice p = price("22.00", "20.00", null);
        p.submitForApproval(UUID.randomUUID(), "x");
        assertTrue(p.approvalRejected());
        assertFalse(p.isLive(D.plusDays(5)));
        assertFalse(p.approvalGranted());
    }

    @Test @DisplayName("creating a price inside the limits submits nothing and is approved")
    void createWithinLimits() {
        FuelPrice current = price("20.00", null, null);
        when(prices.liveComparableStandard(eq(T), any(), any(), any())).thenReturn(List.of(current));
        when(settings.findByTenant(T)).thenReturn(Optional.of(settings("10", null)));
        savedPrices();
        var out = service.create(T, new PriceRequest("DIESEL", null, bd("20.50"), null, D, null, null), UUID.randomUUID());
        assertEquals("APPROVED", out.approvalStatus());
        verify(approvals, never()).submit(any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("creating a price over the limit submits it as a PRICE approval and holds it when approval is needed")
    void createOverLimit() {
        FuelPrice current = price("20.00", null, null);
        when(prices.liveComparableStandard(eq(T), any(), any(), any())).thenReturn(List.of(current));
        when(settings.findByTenant(T)).thenReturn(Optional.of(settings("5", null)));
        ApprovalRequestResponse pending = response("PENDING");
        when(approvals.submit(eq(T), eq("fuel"), eq("PRICE"), any(), any(), any())).thenReturn(pending);
        savedPrices();
        var out = service.create(T, new PriceRequest("DIESEL", null, bd("23.00"), null, D, null, null), UUID.randomUUID());
        assertEquals("PENDING_APPROVAL", out.approvalStatus());
        assertFalse(out.live());
        verify(approvals).submit(eq(T), eq("fuel"), eq("PRICE"), any(), any(), any());
    }

    @Test @DisplayName("when the Approvals engine approves straight away (no rule), the price stays approved")
    void autoApproved() {
        when(prices.liveComparableStandard(eq(T), any(), any(), any())).thenReturn(List.of(price("20.00", null, null)));
        when(settings.findByTenant(T)).thenReturn(Optional.of(settings("5", null)));
        ApprovalRequestResponse approved = response("APPROVED");
        when(approvals.submit(eq(T), eq("fuel"), eq("PRICE"), any(), any(), any())).thenReturn(approved);
        savedPrices();
        var out = service.create(T, new PriceRequest("DIESEL", null, bd("23.00"), null, D, null, null), UUID.randomUUID());
        assertEquals("APPROVED", out.approvalStatus());
    }

    @Test @DisplayName("an approval outcome for a price that is not found, or already decided, does nothing")
    void outcomeIsIdempotent() {
        when(prices.findOwnedForUpdate(eq(T), any())).thenReturn(Optional.empty());
        service.applyApprovalOutcome(T, UUID.randomUUID(), "APPROVED");
        FuelPrice decided = price("22.00", "20.00", null);
        UUID id = decided.getId();
        when(prices.findOwnedForUpdate(T, id)).thenReturn(Optional.of(decided));
        service.applyApprovalOutcome(T, id, "APPROVED");   // already APPROVED, not pending
        verify(prices, never()).save(any());
    }
}
