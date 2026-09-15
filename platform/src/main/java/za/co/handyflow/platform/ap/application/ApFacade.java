package za.co.handyflow.platform.ap.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.application.internal.ApService;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public entry point for other modules that need to trigger AP's own
 * scheduled operations, or hand off a document into AP's own bill
 * lifecycle. Currently exposes two things: the overdue-bill sweep
 * (BillingScheduler's own reason for reaching in originally), and the
 * Supply Chain -> AP hand-off, per the product owner's own explicit
 * design decision: keep ScSupplierInvoice and ApBill as genuinely
 * separate entities representing different business stages, but build
 * the missing bridge between them rather than leaving two unreconciled
 * records. Deliberately minimal, matching this facade's own original
 * stated philosophy: expose only what's actually needed from outside,
 * not a general-purpose AP API.
 */
@Service
@RequiredArgsConstructor
public class ApFacade {

    private final ApService apService;
    private final ApBillRepository billRepository;

    /**
     * Runs the overdue-bill sweep — marks any APPROVED bill whose due date
     * has passed as OVERDUE. Idempotent: an already-OVERDUE bill matches
     * the same query again with no effect, so calling this more than once
     * (or on top of a retried scheduled job) is safe.
     */
    public void markOverdueBills() {
        apService.markOverdueBills();
    }

    /**
     * Creates the AP bill for a Supply Chain invoice that has just been
     * three-way matched and approved — Step 5 of the product owner's own
     * design ("AP handoff... Create ApBill, SOURCE = SUPPLY_CHAIN,
     * SOURCE_REFERENCE = SC-INV-123"). Idempotent by source: if a bill
     * already exists for this exact sourceReference, returns it
     * unchanged rather than creating a duplicate — a retried or
     * re-triggered approval on the Supply Chain side can never create a
     * second financial liability for the same invoice.
     * <p>
     * Starts the new bill at DRAFT — this does NOT auto-approve it. AP's
     * own normal approval workflow owns everything from here, matching
     * "AP owns payment lifecycle from here" exactly — the hand-off's job
     * is only to get the liability correctly represented in AP, not to
     * fast-track it past AP's own controls.
     */
    @Transactional
    public ApBill createBillFromSupplyChainInvoice(TenantId tenantId, UUID supplierId, String supplierName,
                                                    String sourceReference, LocalDate billDate, LocalDate dueDate,
                                                    BigDecimal amount, BigDecimal vatAmount, UUID createdBy) {
        return billRepository.findBySource(tenantId, "SUPPLY_CHAIN", sourceReference)
                .orElseGet(() -> {
                    // FIX: ap_bills has a genuine UNIQUE(tenant_id, bill_number)
                    // constraint (confirmed directly, V33) — reusing the SC
                    // invoice's own invoiceNumber unprefixed risked a real
                    // collision with a manually-entered AP bill that happens
                    // to use the same string. "SC-" prefix makes it visually
                    // distinct in AP's own bill list too, not just collision-safe.
                    String billNumber = "SC-" + sourceReference;
                    return billRepository.save(
                            ApBill.createFromSupplyChain(tenantId, supplierId, supplierName,
                                    billNumber, billDate, dueDate, amount, vatAmount, sourceReference, createdBy));
                });
    }
}