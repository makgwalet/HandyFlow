package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.ap.domain.model.ApRecurringBillTemplate;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.ap.domain.repository.ApRecurringBillTemplateRepository;
import za.co.handyflow.platform.ap.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Separate service, not folded into the already-large ApService — a
 * distinct enough concern (schedule-driven generation vs. direct user
 * action) to warrant its own class, matching how AccountingNotification-
 * Scheduler and similar scheduled-job classes elsewhere in this codebase
 * stay separate from their module's main service.
 * <p>
 * Deliberately builds generated bills directly via ApBill.create()
 * rather than routing through ApService.createBill() — that would need
 * constructing a CreateBillRequest, whose real declared field order
 * isn't available here to verify positionally. ApBill.create()'s
 * signature is directly confirmed from the real entity, so building on
 * that instead avoids guessing at a DTO that can't be checked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApRecurringBillService {

    private final ApRecurringBillTemplateRepository templateRepo;
    private final ApBillRepository                  billRepo;
    private final NotificationService                notificationService;
    private final TenantAdminRecipients              tenantAdminRecipients;

    @Transactional(readOnly = true)
    public List<RecurringBillTemplateResponse> getTemplates(TenantId tenantId) {
        return templateRepo.findAll(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RecurringBillTemplateResponse createTemplate(TenantId tenantId, UUID createdBy,
                                                        CreateRecurringBillTemplateRequest req) {
        ApRecurringBillTemplate t = ApRecurringBillTemplate.create(
                tenantId, req.supplierId(), req.supplierName(), req.category(), req.description(),
                req.amount(), req.vatAmount(), req.dayOfMonth(), req.leadDays(),
                req.notes(), createdBy);
        templateRepo.save(t);
        log.info("Created recurring bill template={} supplier={} amount={} nextDue={}",
                t.getId(), req.supplierName(), req.amount(), t.getNextDueDate());
        return toResponse(t);
    }

    @Transactional
    public RecurringBillTemplateResponse updateTemplate(TenantId tenantId, UUID id,
                                                        UpdateRecurringBillTemplateRequest req) {
        ApRecurringBillTemplate t = findTemplate(tenantId, id);
        t.update(req.description(), req.amount(), req.vatAmount(),
                req.dayOfMonth(), req.leadDays(), req.notes());
        templateRepo.save(t);
        return toResponse(t);
    }

    @Transactional
    public RecurringBillTemplateResponse pauseTemplate(TenantId tenantId, UUID id) {
        ApRecurringBillTemplate t = findTemplate(tenantId, id);
        t.pause();
        templateRepo.save(t);
        log.info("Paused recurring bill template={}", id);
        return toResponse(t);
    }

    @Transactional
    public RecurringBillTemplateResponse resumeTemplate(TenantId tenantId, UUID id) {
        ApRecurringBillTemplate t = findTemplate(tenantId, id);
        t.resume();
        templateRepo.save(t);
        log.info("Resumed recurring bill template={}", id);
        return toResponse(t);
    }

    /**
     * Manual "generate now" — same generation logic the scheduler uses,
     * triggered immediately for one template instead of waiting for the
     * daily job. Useful for testing and for genuine "I need this bill
     * today" cases. Deliberately does NOT check leadDays/nextDueDate
     * timing — generating on demand is an explicit human decision, not
     * something that needs the schedule's own gating applied on top.
     */
    @Transactional
    public BillResponse generateNow(TenantId tenantId, UUID id) {
        ApRecurringBillTemplate t = findTemplate(tenantId, id);
        return generateBill(t);
    }

    /**
     * Called daily by ApRecurringBillScheduler — finds every active
     * template across all tenants whose next due date has entered its
     * own lead window, generates a DRAFT bill for it, and advances
     * nextDueDate by one month. Per-template try/catch isolation, same
     * pattern as ApBillDueSoonScheduler — one bad template must never
     * stop the rest of the batch.
     */
    @Transactional
    public void generateDueBills() {
        LocalDate today = LocalDate.now();
        List<ApRecurringBillTemplate> candidates = templateRepo.findAllActiveAcrossTenants();

        int generated = 0;
        for (ApRecurringBillTemplate t : candidates) {
            if (t.getNextDueDate().isAfter(today.plusDays(t.getLeadDays()))) continue;
            try {
                generateBill(t);
                generated++;
            } catch (Exception e) {
                log.error("Failed to generate recurring bill from template={}: {}",
                        t.getId(), e.getMessage(), e);
            }
        }
        if (generated > 0) {
            log.info("Generated {} recurring bill(s)", generated);
        }
    }

    private BillResponse generateBill(ApRecurringBillTemplate t) {
        BigDecimal totalAmount = t.getAmount().add(t.getVatAmount());

        // REC-{template's own id, first 8 chars}-{due YYYY-MM} — always
        // unique per template per month with no counter table needed;
        // decipherable back to its source template just by looking at it.
        String billNumber = "REC-" + t.getId().toString().substring(0, 8).toUpperCase()
                + "-" + t.getNextDueDate().getYear()
                + "-" + String.format("%02d", t.getNextDueDate().getMonthValue());

        // Same possible-duplicate check createBill() runs manually — a
        // recurring bill generating at the same amount ~30 days apart is
        // exactly the case that check's ±10 day window was designed NOT
        // to flag, so this should stay silent for genuinely normal
        // recurring activity and only warn on something unexpected.
        List<ApBill> possibleDuplicates = billRepo.findPossibleDuplicates(
                t.getTenantId(), t.getSupplierName(), totalAmount,
                t.getNextDueDate().minusDays(10), t.getNextDueDate().plusDays(10));
        if (!possibleDuplicates.isEmpty()) {
            log.warn("Recurring bill generation for template={} found a possible duplicate (existing bill={}) — "
                            + "generating anyway as a DRAFT, still needs manual approval either way",
                    t.getId(), possibleDuplicates.get(0).getId());
        }

        ApBill bill = ApBill.create(
                t.getTenantId(), t.getSupplierId(), t.getSupplierName(),
                billNumber, t.getNextDueDate().minusDays(t.getLeadDays()), t.getNextDueDate(),
                t.getCategory(), t.getDescription(), t.getAmount(), t.getVatAmount(),
                null, null, t.getNotes(), null);
        billRepo.save(bill);

        t.advanceToNextDue(bill.getId());
        templateRepo.save(t);

        log.info("Generated recurring bill={} (#{}) from template={} supplier={} due={}",
                bill.getId(), billNumber, t.getId(), t.getSupplierName(), t.getNextDueDate());

        notifyRecurringBillGenerated(t, bill);

        return toBillResponse(bill);
    }

    private void notifyRecurringBillGenerated(ApRecurringBillTemplate t, ApBill bill) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(t.getTenantId());
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(t.getTenantId())
                .type(NotificationType.BILL_PENDING_APPROVAL)
                .title("Recurring bill generated: " + bill.getSupplierName())
                .message(bill.getSupplierName() + " — " + bill.getBillNumber()
                        + " (R " + bill.getTotalAmount() + ") was auto-generated from a recurring template and is waiting for approval.")
                .actionUrl("/ap/bills/" + bill.getId())
                .sourceModule("ap")
                .sourceEntityId(bill.getId().toString())
                .recipients(recipients)
                .build());
    }

    private ApRecurringBillTemplate findTemplate(TenantId tenantId, UUID id) {
        return templateRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringBillTemplate", id.toString()));
    }

    private RecurringBillTemplateResponse toResponse(ApRecurringBillTemplate t) {
        return new RecurringBillTemplateResponse(
                t.getId(), t.getSupplierId(), t.getSupplierName(), t.getCategory(), t.getDescription(),
                t.getAmount(), t.getVatAmount(), t.getAmount().add(t.getVatAmount()),
                t.getFrequency(), t.getDayOfMonth(), t.getLeadDays(),
                t.getNextDueDate(), t.getLastGeneratedBillId(), t.getLastGeneratedAt(),
                t.isActive(), t.getNotes(), t.getCreatedAt());
    }

    // Local, minimal mapper — avoids reaching across into ApService's own
    // private toBillResponse() (not accessible anyway), and this only
    // ever needs to confirm "a bill was generated", not carry a
    // duplicate-warning field (null here — that's a create-time-only
    // concept, and this generation already logs its own duplicate
    // warning separately above, at the level a human actually reads it).
    private BillResponse toBillResponse(ApBill b) {
        return new BillResponse(
                b.getId(), b.getSupplierId(), b.getSupplierName(),
                b.getBillNumber(), b.getBillDate(), b.getDueDate(),
                b.getCategory(), b.getDescription(),
                b.getAmount(), b.getVatAmount(), b.getTotalAmount(), b.getCurrency(),
                b.getStatus(), b.isOverdue(), b.daysUntilDue(),
                b.getAttachmentUrl() != null, b.getPopUrl() != null,
                b.getPaymentRef(), b.getBatchId(), b.getNotes(),
                b.getJournalEntryId(), b.getPaymentJournalId(),
                b.getFirstApprovedBy(), b.getFirstApprovedAt(),
                b.getPaidAt(), b.getCreatedAt(), null, b.getRejectionReason());
    }
}