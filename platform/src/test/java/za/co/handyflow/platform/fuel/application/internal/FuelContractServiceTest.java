package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ContractDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 7c (B06b): contracts group and report; they never create or change a price. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelContractServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    static final LocalDate START = LocalDate.of(2026, 10, 1), END = LocalDate.of(2026, 12, 31), TODAY = LocalDate.of(2026, 11, 12);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelContractRepository contracts;
    @Mock FuelContractPriceRepository links;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelPriceRepository prices;
    @Mock FuelDeliveryRepository deliveries;
    @Mock CrmFacade crm;
    @InjectMocks FuelContractService service;

    final UUID crmCustomer = UUID.randomUUID();
    FuelCustomerAccount account;
    final List<FuelContract> saved = new ArrayList<>();
    final List<FuelContractPrice> linked = new ArrayList<>();
    final List<FuelDelivery> delivered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, crmCustomer, "MINING_COMPANY", bd("100000"), 30, null, null);
        when(accounts.findActiveById(eq(T), eq(account.getId()))).thenReturn(Optional.of(account));
        when(contracts.save(any())).thenAnswer(i -> { FuelContract c = i.getArgument(0); if (!saved.contains(c)) saved.add(c); return c; });
        when(contracts.findActiveById(eq(T), any())).thenAnswer(i -> saved.stream().filter(c -> c.getId().equals(i.getArgument(1))).findFirst());
        when(contracts.numberTaken(eq(T), any())).thenAnswer(i -> saved.stream().anyMatch(c -> c.getContractNumber().equalsIgnoreCase(i.getArgument(1))));
        when(links.save(any())).thenAnswer(i -> { FuelContractPrice l = i.getArgument(0); if (!linked.contains(l)) linked.add(l); return l; }); // saving a row already held is an update, not a second row
        when(links.findLink(eq(T), any(), any())).thenAnswer(i -> linked.stream().filter(l -> l.getContractId().equals(i.getArgument(1)) && l.getPriceId().equals(i.getArgument(2))).findFirst());
        when(links.findByContract(eq(T), any())).thenAnswer(i -> linked.stream().filter(l -> l.getContractId().equals(i.getArgument(1))).toList());
        when(crm.findCustomerById(eq(T), any())).thenReturn(Optional.empty());
        when(deliveries.findDeliveredBetween(eq(T), any(), any())).thenAnswer(i -> delivered);
    }

    private CreateRequest create(String number, String fuel, String committed) {
        return new CreateRequest(account.getId(), number, "Mine supply 2026", START, END, fuel, committed == null ? null : bd(committed), null);
    }

    private FuelDelivery done(UUID customer, String fuel, String litres) {
        FuelDelivery d = FuelDelivery.create(T, UUID.randomUUID(), customer, Map.of(), fuel, bd(litres), bd("22"), Instant.now(), null, null);
        d.dispatch("D", "V"); d.complete(bd(litres), "R", null, null, null, false, null, null);
        delivered.add(d);
        return d;
    }

    private FuelPrice price(UUID accountId, String fuel) {
        FuelPrice p = FuelPrice.create(T, fuel, accountId, bd("21.50"), BigDecimal.ZERO, START, null, null, BY);
        when(prices.findOwned(eq(T), eq(p.getId()))).thenReturn(Optional.of(p));
        return p;
    }

    @Test @DisplayName("a contract is saved against a customer account and starts active with nothing delivered")
    void createsContract() {
        ContractView v = service.create(T, create("C-001", "DIESEL", "100000"), BY, TODAY);
        assertEquals("ACTIVE", v.status()); assertEquals(0, BigDecimal.ZERO.compareTo(v.deliveredLitres())); assertEquals(0, bd("100000").compareTo(v.litresRemaining()));
        assertEquals(49L, v.daysLeft()); assertEquals(0, v.linkedPrices());
    }

    @Test @DisplayName("a duplicate number, an unknown account and bad dates or commitments are refused")
    void createRefusals() {
        service.create(T, create("C-001", null, null), BY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> service.create(T, create("c-001", null, null), BY, TODAY));
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, new CreateRequest(UUID.randomUUID(), "C-9", "t", START, END, null, null, null), BY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.create(T, new CreateRequest(account.getId(), "C-2", "t", END, START, null, null, null), BY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.create(T, create("C-3", null, "0"), BY, TODAY));
    }

    @Test @DisplayName("litres delivered to that customer, of the contract's fuel, count against the commitment; other customers and fuels do not")
    void reportsAgainstCommitment() {
        ContractView v0 = service.create(T, create("C-001", "DIESEL", "10000"), BY, TODAY);
        done(crmCustomer, "DIESEL", "3000"); done(crmCustomer, "DIESEL", "1500");
        done(UUID.randomUUID(), "DIESEL", "9000");   // someone else
        done(crmCustomer, "PETROL", "800");          // another fuel
        ContractView v = service.get(T, v0.id(), TODAY);
        assertEquals(0, bd("4500").compareTo(v.deliveredLitres()));
        assertEquals(0, bd("45.0").compareTo(v.percentOfCommitment()));
        assertEquals(0, bd("5500").compareTo(v.litresRemaining()));
    }

    @Test @DisplayName("a contract that names no fuel counts every fuel, and one with no commitment shows no percentage")
    void noFuelNoCommitment() {
        ContractView v0 = service.create(T, create("C-001", null, null), BY, TODAY);
        done(crmCustomer, "DIESEL", "1000"); done(crmCustomer, "PETROL", "500");
        ContractView v = service.get(T, v0.id(), TODAY);
        assertEquals(0, bd("1500").compareTo(v.deliveredLitres())); assertNull(v.percentOfCommitment()); assertNull(v.litresRemaining());
    }

    @Test @DisplayName("status follows the dates: upcoming, active, expired; and nothing counts before it starts")
    void statusByDate() {
        ContractView v0 = service.create(T, create("C-001", null, "1000"), BY, TODAY);
        assertEquals("UPCOMING", service.get(T, v0.id(), START.minusDays(1)).status());
        assertNull(service.get(T, v0.id(), START.minusDays(1)).daysLeft());
        assertEquals("EXPIRED", service.get(T, v0.id(), END.plusDays(1)).status());
        assertNull(service.get(T, v0.id(), END.plusDays(1)).daysLeft());
        assertEquals(0L, service.get(T, v0.id(), END).daysLeft());
    }

    @Test @DisplayName("cancelling needs a reason, ends the contract, and a cancelled contract cannot be edited or have prices linked")
    void cancelling() {
        ContractView v0 = service.create(T, create("C-001", null, null), BY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> service.cancel(T, v0.id(), new CancelRequest(" "), TODAY));
        assertEquals("CANCELLED", service.cancel(T, v0.id(), new CancelRequest("Customer left"), TODAY).status());
        assertThrows(IllegalStateException.class, () -> service.update(T, v0.id(), new UpdateRequest("x", START, END, null, null, null), TODAY));
        FuelPrice p = price(account.getId(), "DIESEL");
        assertThrows(IllegalStateException.class, () -> service.linkPrice(T, v0.id(), p.getId(), BY, TODAY));
        assertThrows(IllegalStateException.class, () -> service.cancel(T, v0.id(), new CancelRequest("again"), TODAY));
    }

    @Test @DisplayName("an existing price of the same account can be linked, and linking changes nothing about the price")
    void linksPrice() {
        ContractView v0 = service.create(T, create("C-001", "DIESEL", null), BY, TODAY);
        FuelPrice p = price(account.getId(), "DIESEL");
        ContractView v = service.linkPrice(T, v0.id(), p.getId(), BY, TODAY);
        assertEquals(1, v.linkedPrices()); assertEquals(0, bd("21.50").compareTo(v.prices().get(0).pricePerLitre()));
        service.linkPrice(T, v0.id(), p.getId(), BY, TODAY);   // again: still one link
        assertEquals(1, linked.size());
        verify(prices, never()).save(any());
        verify(prices, never()).delete(any());
    }

    @Test @DisplayName("a price of another account, a standard price, or another fuel cannot be linked")
    void linkRefusals() {
        ContractView v0 = service.create(T, create("C-001", "DIESEL", null), BY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> service.linkPrice(T, v0.id(), price(UUID.randomUUID(), "DIESEL").getId(), BY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.linkPrice(T, v0.id(), price(null, "DIESEL").getId(), BY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.linkPrice(T, v0.id(), price(account.getId(), "PETROL").getId(), BY, TODAY));
        assertThrows(ResourceNotFoundException.class, () -> service.linkPrice(T, v0.id(), UUID.randomUUID(), BY, TODAY));
        assertTrue(linked.isEmpty());
    }

    @Test @DisplayName("unlinking removes only the link")
    void unlinks() {
        ContractView v0 = service.create(T, create("C-001", null, null), BY, TODAY);
        FuelPrice p = price(account.getId(), "DIESEL");
        service.linkPrice(T, v0.id(), p.getId(), BY, TODAY);
        service.unlinkPrice(T, v0.id(), p.getId(), TODAY);
        verify(links).delete(any(FuelContractPrice.class));
        verify(prices, never()).delete(any());
    }

    @Test @DisplayName("editing changes the dates and commitment but not the account or number")
    void edits() {
        ContractView v0 = service.create(T, create("C-001", null, "1000"), BY, TODAY);
        ContractView v = service.update(T, v0.id(), new UpdateRequest("Renamed", START, END.plusMonths(6), "DIESEL", bd("2000"), "Net 30"), TODAY);
        assertEquals("Renamed", v.title()); assertEquals(END.plusMonths(6), v.endDate()); assertEquals("C-001", v.contractNumber()); assertEquals(account.getId(), v.accountId());
        assertEquals(0, bd("2000").compareTo(v.committedLitres()));
    }

    // ── volume ladder ────────────────────────────────────────────────────────

    private FuelPrice stepPrice(String fuel, String price) {
        FuelPrice p = FuelPrice.create(T, fuel, account.getId(), bd(price), BigDecimal.ZERO, START, null, null, BY);
        when(prices.findOwned(eq(T), eq(p.getId()))).thenReturn(Optional.of(p));
        return p;
    }

    @Test @DisplayName("linking with a volume sets the step; linking again changes it; without a volume it is 0 as before")
    void linkWithVolume() {
        ContractView c = service.create(T, create("C-V1", "DIESEL", null), BY, TODAY);
        FuelPrice base = stepPrice("DIESEL", "21.50"), step = stepPrice("DIESEL", "20.50");
        service.linkPrice(T, c.id(), base.getId(), BY, TODAY);
        service.linkPrice(T, c.id(), step.getId(), bd("5000"), BY, TODAY);
        assertEquals(0, bd("0").compareTo(linked.get(0).getFromLitres()));
        assertEquals(0, bd("5000").compareTo(linked.get(1).getFromLitres()));
        service.linkPrice(T, c.id(), step.getId(), bd("7500"), BY, TODAY);
        assertEquals(2, linked.size());
        assertEquals(0, bd("7500").compareTo(linked.get(1).getFromLitres()));
    }

    @Test @DisplayName("two prices of one fuel cannot start at the same volume, and a negative volume is refused")
    void sameVolumeRefused() {
        ContractView c = service.create(T, create("C-V2", "DIESEL", null), BY, TODAY);
        FuelPrice a = stepPrice("DIESEL", "20.50"), b = stepPrice("DIESEL", "20.00");
        service.linkPrice(T, c.id(), a.getId(), bd("5000"), BY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> service.linkPrice(T, c.id(), b.getId(), bd("5000"), BY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.linkPrice(T, c.id(), b.getId(), bd("-1"), BY, TODAY));
    }

    @Test @DisplayName("the step in effect follows the volume delivered so far, and is shown on the contract")
    void stepFollowsVolume() {
        ContractView c = service.create(T, create("C-V3", "DIESEL", null), BY, TODAY);
        FuelPrice base = stepPrice("DIESEL", "21.50"), s1 = stepPrice("DIESEL", "20.50"), s2 = stepPrice("DIESEL", "19.50");
        service.linkPrice(T, c.id(), base.getId(), BY, TODAY);
        service.linkPrice(T, c.id(), s1.getId(), bd("5000"), BY, TODAY);
        service.linkPrice(T, c.id(), s2.getId(), bd("20000"), BY, TODAY);
        assertEquals(base.getId(), inEffect(service.get(T, c.id(), TODAY)));
        done(crmCustomer, "DIESEL", "6000");
        assertEquals(s1.getId(), inEffect(service.get(T, c.id(), TODAY)));
        done(crmCustomer, "DIESEL", "14000");
        assertEquals(s2.getId(), inEffect(service.get(T, c.id(), TODAY)));
    }

    @Test @DisplayName("a step waiting for approval does not apply, so the next lower step does")
    void pendingStepSkipped() {
        ContractView c = service.create(T, create("C-V4", "DIESEL", null), BY, TODAY);
        FuelPrice base = stepPrice("DIESEL", "21.50"), s1 = stepPrice("DIESEL", "20.50");
        s1.submitForApproval(UUID.randomUUID(), "volume discount");
        service.linkPrice(T, c.id(), base.getId(), BY, TODAY);
        service.linkPrice(T, c.id(), s1.getId(), bd("5000"), BY, TODAY);
        done(crmCustomer, "DIESEL", "9000");
        assertEquals(base.getId(), inEffect(service.get(T, c.id(), TODAY)));
    }

    @Test @DisplayName("a contract with no step above 0 litres has no ladder, so nothing is marked in effect")
    void noLadderNothingInEffect() {
        ContractView c = service.create(T, create("C-V5", "DIESEL", null), BY, TODAY);
        service.linkPrice(T, c.id(), stepPrice("DIESEL", "21.50").getId(), BY, TODAY);
        assertNull(inEffect(service.get(T, c.id(), TODAY)));
    }

    private UUID inEffect(ContractView v) {
        return v.prices().stream().filter(p -> p.inEffect()).map(p -> p.priceId()).findFirst().orElse(null);
    }
}
