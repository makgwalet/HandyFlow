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
import za.co.handyflow.platform.fuel.domain.model.FuelContract;
import za.co.handyflow.platform.fuel.domain.model.FuelContractPrice;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
import za.co.handyflow.platform.fuel.domain.repository.FuelContractPriceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelContractRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPriceRepository;
import za.co.handyflow.platform.fuel.dto.ResolvedPriceResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelContractTierPricingTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final LocalDate START = LocalDate.of(2026, 10, 1), END = LocalDate.of(2026, 12, 31), TODAY = LocalDate.of(2026, 11, 12);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelContractRepository contracts;
    @Mock FuelContractPriceRepository links;
    @Mock FuelPriceRepository prices;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelContractService contractService;
    @InjectMocks FuelContractTierPricing tiers;

    FuelCustomerAccount account;
    final List<FuelContractPrice> rows = new ArrayList<>();
    BigDecimal delivered = BigDecimal.ZERO;
    FuelContract contract;

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, UUID.randomUUID(), "OTHER", bd("100000"), 30, null, null);
        when(accounts.findActiveById(T, account.getId())).thenReturn(Optional.of(account));
        contract = contract("DIESEL");
        when(contracts.findAllActive(T)).thenAnswer(i -> new ArrayList<>(List.of(contract)));
        when(links.findByContract(eq(T), any())).thenAnswer(i -> rows);
        when(contractService.deliveredLitres(eq(T), any(), eq(account), any())).thenAnswer(i -> delivered);
    }

    private FuelContract contract(String fuel) {
        return FuelContract.create(T, account.getId(), "C-" + UUID.randomUUID().toString().substring(0, 4), "Supply", START, END, fuel, null, null, null);
    }

    private FuelPrice step(String price, String from) {
        FuelPrice p = FuelPrice.create(T, "DIESEL", account.getId(), bd(price), BigDecimal.ZERO, START, null, null, null);
        when(prices.findOwned(T, p.getId())).thenReturn(Optional.of(p));
        FuelContractPrice l = FuelContractPrice.of(T, contract.getId(), p.getId(), null);
        l.setFromLitres(bd(from));
        rows.add(l);
        return p;
    }

    @Test @DisplayName("with a ladder, the step the delivered volume has reached is used")
    void picksStep() {
        step("21.50", "0"); FuelPrice s1 = step("20.50", "5000"); step("19.50", "20000");
        delivered = bd("6000");
        ResolvedPriceResponse r = tiers.resolve(T, account.getId(), "DIESEL", TODAY).orElseThrow();
        assertEquals(s1.getId(), r.priceId());
        assertEquals(0, bd("20.50").compareTo(r.pricePerLitre()));
        assertEquals("CONTRACT", r.source());
    }

    @Test @DisplayName("a contract with no ladder does nothing, so ordinary pricing runs")
    void noLadder() {
        step("21.50", "0");
        assertTrue(tiers.resolve(T, account.getId(), "DIESEL", TODAY).isEmpty());
    }

    @Test @DisplayName("two active ladders for the same customer and fuel are not guessed between")
    void ambiguous() {
        step("21.50", "0"); step("20.50", "5000");
        FuelContract second = contract("DIESEL");
        when(contracts.findAllActive(T)).thenAnswer(i -> new ArrayList<>(List.of(contract, second)));
        assertTrue(tiers.resolve(T, account.getId(), "DIESEL", TODAY).isEmpty());
    }

    @Test @DisplayName("another fuel, another customer, or a contract that is not active today gets nothing")
    void scope() {
        step("21.50", "0"); step("20.50", "5000"); delivered = bd("9000");
        assertTrue(tiers.resolve(T, account.getId(), "PETROL", TODAY).isEmpty());
        assertTrue(tiers.resolve(T, UUID.randomUUID(), "DIESEL", TODAY).isEmpty());
        assertTrue(tiers.resolve(T, account.getId(), "DIESEL", END.plusDays(1)).isEmpty());
        assertTrue(tiers.resolve(T, null, "DIESEL", TODAY).isEmpty());
    }

    @Test @DisplayName("a contract that names no fuel covers every fuel its linked prices are for")
    void contractWithoutFuel() {
        contract = contract(null);
        FuelPrice s = step("20.50", "5000"); delivered = bd("9000");
        assertEquals(s.getId(), tiers.resolve(T, account.getId(), "DIESEL", TODAY).orElseThrow().priceId());
    }

    @Test @DisplayName("when no step qualifies the answer is empty so ordinary pricing runs")
    void nothingQualifies() {
        step("20.50", "5000"); delivered = bd("100");
        assertTrue(tiers.resolve(T, account.getId(), "DIESEL", TODAY).isEmpty());
    }
}
