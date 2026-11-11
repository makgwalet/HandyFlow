package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPriceRepository;
import za.co.handyflow.platform.fuel.dto.PriceRequest;
import za.co.handyflow.platform.fuel.dto.ResolvedPriceResponse;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPricingServiceTest {

    @Mock FuelPriceRepository prices;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock CrmFacade crm;
    @InjectMocks FuelPricingService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final LocalDate D = LocalDate.of(2026, 1, 1);
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    final UUID acct = UUID.randomUUID();

    private FuelPrice price(UUID account, String p, String min) {
        return FuelPrice.create(T, "DIESEL", account, bd(p), bd(min), D, null, null, null);
    }

    @Test @DisplayName("a customer's contract price beats the standard price, even when the standard one is lower")
    void contractBeatsStandard() {
        var r = FuelPricingService.pick(List.of(price(null, "21.00", "0"), price(acct, "22.40", "0")), acct, bd("1000")).orElseThrow();
        assertEquals("CONTRACT", r.source());
        assertEquals(0, bd("22.40").compareTo(r.pricePerLitre()));
    }

    @Test @DisplayName("the highest volume break the quantity reaches applies")
    void volumeBreaks() {
        var list = List.of(price(null, "22.00", "0"), price(null, "21.50", "10000"), price(null, "21.00", "30000"));
        assertEquals(0, bd("22.00").compareTo(FuelPricingService.pick(list, null, bd("5000")).orElseThrow().pricePerLitre()));
        assertEquals(0, bd("21.50").compareTo(FuelPricingService.pick(list, null, bd("10000")).orElseThrow().pricePerLitre()));
        assertEquals(0, bd("21.00").compareTo(FuelPricingService.pick(list, null, bd("35000")).orElseThrow().pricePerLitre()));
    }

    @Test @DisplayName("a contract whose volume break is not reached falls back to the standard price")
    void contractBreakNotReached() {
        var r = FuelPricingService.pick(List.of(price(null, "22.00", "0"), price(acct, "20.00", "50000")), acct, bd("1000")).orElseThrow();
        assertEquals("STANDARD", r.source());
    }

    @Test @DisplayName("another customer's contract price is never used; no price at all gives empty")
    void otherCustomerIgnored() {
        assertTrue(FuelPricingService.pick(List.of(price(UUID.randomUUID(), "10.00", "0")), acct, bd("1000")).isEmpty());
        assertTrue(FuelPricingService.pick(List.of(), acct, bd("1000")).isEmpty());
    }

    @Test @DisplayName("price validation: negative price, negative break, end before start")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> FuelPrice.create(T, "DIESEL", null, bd("-1"), null, D, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FuelPrice.create(T, "DIESEL", null, bd("1"), bd("-5"), D, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FuelPrice.create(T, "DIESEL", null, bd("1"), null, D, D.minusDays(1), null, null));
        assertThrows(IllegalArgumentException.class, () -> FuelPrice.create(T, " ", null, bd("1"), null, D, null, null, null));
    }

    @Test @DisplayName("an ended price stops applying after its last day and cannot be ended twice")
    void ending() {
        FuelPrice p = price(null, "22", "0");
        assertTrue(p.isLive(D.plusDays(100)));
        p.end(D.plusDays(10));
        assertTrue(p.isLive(D.plusDays(10)) == false, "ended_at set means no longer live");
        assertThrows(IllegalStateException.class, () -> p.end(D.plusDays(20)));
        assertThrows(IllegalArgumentException.class, () -> price(null, "22", "0").end(D.minusDays(1)));
    }

    @Test @DisplayName("an overlapping price for the same product and break is refused and not saved")
    void overlapRefused() {
        when(prices.overlapStandard(eq(T), any(), any(), any(), any())).thenReturn(List.of(price(null, "22", "0")));
        assertThrows(ConflictException.class, () -> service.create(T, new PriceRequest("DIESEL", null, bd("23"), null, D, null, null), null));
        verify(prices, never()).save(any());
    }

    @Test @DisplayName("a contract price needs one of this tenant's accounts")
    void contractNeedsAccount() {
        when(accounts.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, new PriceRequest("DIESEL", acct, bd("23"), null, D, null, null), null));
    }

    @Test @DisplayName("ending another tenant's price is not found")
    void endIsolation() {
        when(prices.findOwned(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.end(T, UUID.randomUUID(), null));
    }

    @Test @DisplayName("resolve uses the standard query when there is no account")
    void resolveNoAccount() {
        when(prices.liveStandard(eq(T), eq("DIESEL"), any())).thenReturn(List.of(price(null, "22", "0")));
        Optional<ResolvedPriceResponse> r = service.resolve(T, null, "DIESEL", bd("100"), D);
        assertTrue(r.isPresent());
        verify(prices, never()).liveForAccount(any(), any(), any(), any());
    }
}
