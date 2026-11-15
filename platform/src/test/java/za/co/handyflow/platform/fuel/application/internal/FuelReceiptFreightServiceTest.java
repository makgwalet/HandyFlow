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
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.model.FuelReceiptFreightAdjustment;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptFreightAdjustmentRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.FreightAdjustmentDtos.AddFreightRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Freight that arrives after a receipt: only the share that belongs to fuel still in the tank moves the tank's cost. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelReceiptFreightServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelReceiptRepository receipts;
    @Mock FuelTankRepository tanks;
    @Mock FuelReceiptFreightAdjustmentRepository adjustments;
    @InjectMocks FuelReceiptFreightService service;

    FuelTank tank;
    FuelReceipt receipt;
    final List<FuelReceiptFreightAdjustment> saved = new ArrayList<>();

    /** A tank that received 5,000 L at R20 (so it holds 5,000 L at R20) and a receipt for those litres. */
    @BeforeEach
    void setUp() {
        tank = FuelTank.create(T, "Diesel 1", "DIESEL", bd("20000"), "Yard", null);
        tank.addStock(bd("5000"), bd("20"));
        receipt = FuelReceipt.create(T, tank.getId(), null, bd("5000"), bd("20"), Instant.now(), null, null, bd("0"), bd("5000"));
        when(receipts.findActiveById(T, receipt.getId())).thenReturn(Optional.of(receipt));
        when(tanks.findActiveByIdForUpdate(T, tank.getId())).thenReturn(Optional.of(tank));
        when(adjustments.saveAndFlush(any())).thenAnswer(i -> { saved.add(i.getArgument(0)); return i.getArgument(0); });
        when(adjustments.findForReceipt(T, receipt.getId())).thenAnswer(i -> new ArrayList<>(saved));
    }

    @Test @DisplayName("all the fuel is still in the tank: the whole amount goes into the tank's cost")
    void allInTank() {
        var v = service.add(T, receipt.getId(), new AddFreightRequest(bd("1500"), "Carrier inv 88"), BY);
        assertEquals(0, bd("20.3000").compareTo(tank.getCostPerLitreWac())); // 1500 / 5000 = 0.30
        var a = v.adjustments().get(0);
        assertEquals(0, bd("1500").compareTo(a.absorbedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(a.unabsorbedAmount()));
        assertEquals(0, bd("20.0000").compareTo(a.costBefore()));
        assertEquals(0, bd("20.3000").compareTo(a.costAfter()));
        assertEquals(0, bd("1500.00").compareTo(v.freightTotal()));
    }

    @Test @DisplayName("part of the fuel is already sold: only that share goes into the tank, the rest is recorded as unabsorbed")
    void partSold() {
        tank.removeStock(bd("3000")); // 2,000 L left of the 5,000 L received
        var v = service.add(T, receipt.getId(), new AddFreightRequest(bd("1000"), "Carrier inv 89"), BY);
        var a = v.adjustments().get(0);
        assertEquals(0, bd("400.00").compareTo(a.absorbedAmount()));   // 1000 x 2000 / 5000
        assertEquals(0, bd("600.00").compareTo(a.unabsorbedAmount()));
        assertEquals(0, bd("20.2000").compareTo(tank.getCostPerLitreWac())); // 400 / 2000 = 0.20
    }

    @Test @DisplayName("an empty tank takes nothing: the whole amount is unabsorbed and the cost does not change")
    void emptyTank() {
        tank.removeStock(bd("5000"));
        var a = service.add(T, receipt.getId(), new AddFreightRequest(bd("800"), "Late invoice"), BY).adjustments().get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(a.absorbedAmount()));
        assertEquals(0, bd("800.00").compareTo(a.unabsorbedAmount()));
        assertEquals(0, bd("20.0000").compareTo(tank.getCostPerLitreWac()));
    }

    @Test @DisplayName("fuel from later receipts in the tank does not make the share bigger than the receipt's litres")
    void shareCappedAtReceiptLitres() {
        tank.addStock(bd("5000"), bd("22")); // 10,000 L in the tank, WAC 21
        var a = service.add(T, receipt.getId(), new AddFreightRequest(bd("1000"), "Carrier"), BY).adjustments().get(0);
        assertEquals(0, bd("1000.00").compareTo(a.absorbedAmount())); // receipt's 5,000 L fits inside the 10,000 L
        assertEquals(0, bd("21.1000").compareTo(tank.getCostPerLitreWac())); // 1000 / 10000 = 0.10
    }

    @Test @DisplayName("each addition is kept and the receipt's freight is their sum with the original; the supplier's figures do not change")
    void accumulatesAndKeepsSupplierFigures() {
        receipt.recordFreight(bd("500"), "At receipt");
        service.add(T, receipt.getId(), new AddFreightRequest(bd("100"), "First"), BY);
        var v = service.add(T, receipt.getId(), new AddFreightRequest(bd("250"), "Second"), BY);
        assertEquals(2, v.adjustments().size());
        assertEquals(0, bd("850.00").compareTo(v.freightTotal()));
        assertEquals(0, bd("20").compareTo(receipt.getPricePerLitre()));
        assertEquals(0, bd("100000.00").compareTo(receipt.getTotalCost()));
    }

    @Test @DisplayName("zero or negative freight, or no note, is refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.add(T, receipt.getId(), new AddFreightRequest(BigDecimal.ZERO, "x"), BY));
        assertThrows(IllegalArgumentException.class, () -> service.add(T, receipt.getId(), new AddFreightRequest(bd("-5"), "x"), BY));
        assertThrows(IllegalArgumentException.class, () -> service.add(T, receipt.getId(), new AddFreightRequest(bd("5"), "  "), BY));
        verify(tanks, never()).save(any());
    }

    @Test @DisplayName("a reversed receipt cannot take freight, and nothing is saved")
    void reversedRefused() {
        receipt.reverse(BY, "Wrong tank");
        assertThrows(IllegalStateException.class, () -> service.add(T, receipt.getId(), new AddFreightRequest(bd("100"), "x"), BY));
        verify(adjustments, never()).saveAndFlush(any());
    }

    @Test @DisplayName("an unknown receipt is not found")
    void unknown() {
        when(receipts.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.add(T, UUID.randomUUID(), new AddFreightRequest(bd("100"), "x"), BY));
    }
}
