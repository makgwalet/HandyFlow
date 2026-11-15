package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.model.FuelReceiptFreightAdjustment;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptFreightAdjustmentRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.FreightAdjustmentDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Freight that arrives after fuel was received (the carrier's invoice comes later). Each addition is kept as its own record and the receipt's freight
 * total is their sum with the original. The tank mixes fuel, so it cannot say which litres came from which receipt; the share that belongs to fuel
 * still in the tank is taken as the lesser of the receipt's litres and the tank's current litres. That share is added to the tank's cost
 * (weighted average moves by share / litres in the tank). The rest belongs to fuel already sold: it is recorded as unabsorbed and is never pushed
 * back into deliveries that were already costed, so closed margin reports do not move. The supplier's price and total cost never change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelReceiptFreightService {

    private final FuelReceiptRepository receipts;
    private final FuelTankRepository tanks;
    private final FuelReceiptFreightAdjustmentRepository adjustments;

    @Transactional
    public ReceiptFreightView add(TenantId tenantId, UUID receiptId, AddFreightRequest req, UUID by) {
        if (req.amount() == null || req.amount().signum() <= 0) throw new IllegalArgumentException("The freight must be more than zero.");
        if (req.note() == null || req.note().isBlank()) throw new IllegalArgumentException("Say what the freight is for, for example the carrier invoice number.");
        FuelReceipt receipt = receipts.findActiveById(tenantId, receiptId).orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId.toString()));
        FuelTank tank = tanks.findActiveByIdForUpdate(tenantId, receipt.getTankId()).orElseThrow(() -> new ResourceNotFoundException("Tank", receipt.getTankId().toString()));
        BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
        receipt.addLateFreight(amount); // refuses a reversed receipt
        BigDecimal litresInTank = tank.getCurrentLitres();
        BigDecimal share = litresInTank.min(receipt.getLitresReceived());
        BigDecimal absorbed = amount.multiply(share).divide(receipt.getLitresReceived(), 2, RoundingMode.HALF_UP).min(amount);
        BigDecimal before = tank.getCostPerLitreWac();
        if (before == null || litresInTank.signum() <= 0) absorbed = BigDecimal.ZERO; // nothing in the tank to carry it
        tank.absorbCost(absorbed);
        tanks.save(tank);
        receipts.save(receipt);
        FuelReceiptFreightAdjustment a = adjustments.saveAndFlush(FuelReceiptFreightAdjustment.of(tenantId, receiptId, tank.getId(), amount, req.note().trim().length() > 300 ? req.note().trim().substring(0, 300) : req.note().trim(),
                litresInTank, absorbed, before, tank.getCostPerLitreWac(), by));
        log.info("Late freight R{} added to receipt {}: R{} into tank cost, R{} for fuel already sold", amount, receiptId, absorbed, a.getUnabsorbedAmount());
        return view(receipt, adjustments.findForReceipt(tenantId, receiptId));
    }

    @Transactional(readOnly = true)
    public ReceiptFreightView history(TenantId tenantId, UUID receiptId) {
        FuelReceipt receipt = receipts.findActiveById(tenantId, receiptId).orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId.toString()));
        return view(receipt, adjustments.findForReceipt(tenantId, receiptId));
    }

    private ReceiptFreightView view(FuelReceipt r, List<FuelReceiptFreightAdjustment> rows) {
        List<FreightAdjustmentView> out = rows.stream().map(a -> new FreightAdjustmentView(a.getId(), a.getAmount(), a.getNote(), a.getLitresInTank(), a.getAbsorbedAmount(),
                a.getUnabsorbedAmount(), a.getCostBefore(), a.getCostAfter(), a.getCreatedAt(), a.getCreatedBy())).toList();
        return new ReceiptFreightView(r.getId(), r.getFreightAmount(), r.landedPricePerLitre(), out);
    }
}
