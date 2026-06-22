package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.domain.model.*;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.domain.repository.RecurringScheduleRepository;
import za.co.handyflow.platform.invoicing.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringScheduleService {

    private final RecurringScheduleRepository scheduleRepo;
    private final InvoiceRepository invoiceRepo;
    private final CrmFacade crmFacade;
    private final InvoiceNumberGenerator     invoiceNumberGenerator;
    private final InvoiceQueryService invoiceQueryService;

// ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RecurringScheduleResponse> getSchedules(TenantId tenantId, Pageable pageable) {
        return scheduleRepo.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RecurringScheduleResponse getSchedule(TenantId tenantId, UUID id) {
        return scheduleRepo.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id.toString()));
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public RecurringScheduleResponse createSchedule(TenantId tenantId,
                                                    CreateRecurringScheduleRequest req) {
        boolean hasCustomer = req.customerId() != null;
        boolean hasWalkin   = req.walkinClientName() != null && !req.walkinClientName().isBlank();

        if (!hasCustomer && !hasWalkin) {
            throw new IllegalArgumentException(
                    "Either a customer or a walk-in client name must be provided");
        }
        if (hasCustomer && !crmFacade.customerExists(tenantId, req.customerId())) {
            throw new ResourceNotFoundException("Customer", req.customerId().toString());
        }

        RecurringFrequency freq;
        try {
            freq = RecurringFrequency.valueOf(req.frequency().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid frequency: " + req.frequency());
        }

        if (freq == RecurringFrequency.CUSTOM && req.customIntervalDays() == null) {
            throw new IllegalArgumentException("customIntervalDays is required for CUSTOM frequency");
        }

        var schedule = RecurringSchedule.create(
                tenantId,
                req.customerId(),
                req.title(),
                req.notes(),
                freq,
                req.frequencyDay(),
                req.customIntervalDays(),
                req.startDate(),
                req.endDate(),
                req.walkinClientName(),
                req.walkinClientEmail(),
                req.walkinClientPhone()
        );

        scheduleRepo.save(schedule);
        log.info("Created recurring schedule={} tenant={} freq={}", schedule.getId(), tenantId, freq);
        return toResponse(schedule);
    }

    @Transactional
    public RecurringScheduleResponse addLineItem(TenantId tenantId, UUID scheduleId,
                                                 AddLineItemRequest req) {
        var schedule = findActive(tenantId, scheduleId);

        BigDecimal vatRate = req.vatRate() != null ? req.vatRate() : new BigDecimal("15.00");

        var li = RecurringLineItem.create(
                schedule, tenantId,
                req.catalogueItemId(), req.description(),
                req.unit(), req.quantity(), req.unitPrice(), vatRate,
                schedule.getLineItems().size()
        );

        schedule.addLineItem(li);
        scheduleRepo.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public RecurringScheduleResponse pauseSchedule(TenantId tenantId, UUID id) {
        var schedule = findActive(tenantId, id);
        schedule.pause();
        scheduleRepo.save(schedule);
        log.info("Paused recurring schedule={}", id);
        return toResponse(schedule);
    }

    @Transactional
    public RecurringScheduleResponse resumeSchedule(TenantId tenantId, UUID id) {
        var schedule = findActive(tenantId, id);
        schedule.resume();
        scheduleRepo.save(schedule);
        log.info("Resumed recurring schedule={}", id);
        return toResponse(schedule);
    }

    @Transactional
    public void cancelSchedule(TenantId tenantId, UUID id) {
        var schedule = findActive(tenantId, id);
        schedule.cancel();
        scheduleRepo.save(schedule);
        log.info("Cancelled recurring schedule={}", id);
    }

    // ── Internal helper used by InvoicingScheduler ────────────────────────────

    /** Called by the scheduler — not exposed over HTTP. */
    RecurringSchedule load(UUID id) {
        return scheduleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id.toString()));
    }

    void save(RecurringSchedule s) { scheduleRepo.save(s); }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private RecurringScheduleResponse toResponse(RecurringSchedule s) {
        var lineItems = s.getLineItems().stream()
                .map(li -> new LineItemResponse(
                        li.getId(), li.getCatalogueItemId(),
                        li.getDescription(), li.getUnit(),
                        li.getQuantity(), li.getUnitPrice(),
                        li.getVatRate(), li.getLineTotal(), li.getVatAmount()
                )).toList();

        return new RecurringScheduleResponse(
                // ── core (21 existing fields) ──────────────────────────────
                s.getId(),
                s.getTitle(),
                s.getNotes(),
                s.getStatus().name(),
                s.getFrequency().name(),
                s.getFrequencyDay(),
                s.getCustomIntervalDays(),
                s.getCustomerId(),
                s.getStartDate(),
                s.getEndDate(),
                s.getNextRunAt(),
                s.getLastRunAt(),
                s.getSubtotal(),
                s.getVatTotal(),
                s.getTotal(),
                s.getCurrency(),
                lineItems,
                s.getCreatedAt(),
                s.getWalkinClientName(),
                s.getWalkinClientEmail(),
                s.getWalkinClientPhone(),
                // ── variable-hours contract fields (9 new fields) ──────────
                s.isVariableHours(),
                s.getRatePerHour(),
                s.getMinimumHoursPerCycle(),
                s.getHoursVatRate(),
                s.getContractStartDate(),
                s.getContractEndDate(),
                s.getContractedTotalHours(),
                s.getTotalHoursBilled() != null ? s.getTotalHoursBilled() : java.math.BigDecimal.ZERO,
                s.remainingCycles()
        );
    }

    @Transactional
    public RecurringScheduleResponse createVariableHoursContract(
            TenantId tenantId,
            CreateVariableHoursContractRequest req) {

        boolean hasCustomer = req.customerId() != null;
        boolean hasWalkin   = req.walkinClientName() != null && !req.walkinClientName().isBlank();
        if (!hasCustomer && !hasWalkin)
            throw new IllegalArgumentException("Customer or walk-in name required");
        if (hasCustomer && !crmFacade.customerExists(tenantId, req.customerId()))
            throw new ResourceNotFoundException("Customer", req.customerId().toString());

        RecurringFrequency freq;
        try { freq = RecurringFrequency.valueOf(req.frequency().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid frequency: " + req.frequency()); }

        var schedule = RecurringSchedule.createVariableHoursContract(
                tenantId, req.customerId(), req.title(), req.notes(),
                freq,
                req.ratePerHour(),
                req.minimumHoursPerCycle(),
                req.hoursVatRate(),
                req.contractStartDate(),
                req.contractEndDate(),
                req.contractedTotalHours(),
                req.walkinClientName(),
                req.walkinClientEmail(),
                req.walkinClientPhone()
        );

        scheduleRepo.save(schedule);
        log.info("Created variable-hours contract={} tenant={} rate={}/hr minHrs={}",
                schedule.getId(), tenantId, req.ratePerHour(), req.minimumHoursPerCycle());
        return toResponse(schedule);
    }

    /**
     * Operator logs actual hours for the current cycle.
     * If actualHours < minimumHoursPerCycle, the minimum is billed.
     * Generates and auto-issues an invoice from the logged hours.
     */
    @Transactional
    public InvoiceResponse logCycleHours(TenantId tenantId, UUID scheduleId,
                                         LogCycleHoursRequest req) {
        var schedule = findActive(tenantId, scheduleId);

        if (!schedule.isVariableHours()) {
            throw new IllegalStateException(
                    "This schedule uses fixed line items. Use the standard invoice flow.");
        }

        BigDecimal billed    = schedule.resolveBillableHours(req.actualHours());
        boolean    minimised = billed.compareTo(req.actualHours()) > 0;

        // Build the line description
        String description = schedule.getTitle()
                + " — " + req.periodLabel()
                + " (" + req.actualHours().stripTrailingZeros().toPlainString() + "h worked"
                + (minimised ? ", " + billed.stripTrailingZeros().toPlainString() + "h billed — minimum applied)" : ")");

        String invoiceNumber = invoiceNumberGenerator.next(tenantId);

        BigDecimal lineTotal = billed.multiply(schedule.getRatePerHour())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal vatRate   = schedule.getHoursVatRate() != null
                ? schedule.getHoursVatRate() : new BigDecimal("15.00");
        BigDecimal vatAmount = lineTotal.multiply(vatRate)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal total     = lineTotal.add(vatAmount);

        var invoice = Invoice.createFromSchedule(
                tenantId, schedule.getCustomerId(), schedule.getId(),
                invoiceNumber, schedule.getTitle(),
                lineTotal, vatAmount, total,
                schedule.getWalkinClientName(),
                schedule.getWalkinClientEmail(),
                schedule.getWalkinClientPhone()
        );

        var li = InvoiceLineItem.create(
                invoice, tenantId, null,
                description, "hours",
                billed, schedule.getRatePerHour(), vatRate, 0
        );
        invoice.addLineItem(li);
        invoice.recalculateTotals();

        // Add operator notes as a second line if provided
        if (req.operatorNotes() != null && !req.operatorNotes().isBlank()) {
            var noteLine = InvoiceLineItem.create(
                    invoice, tenantId, null,
                    "Notes: " + req.operatorNotes(),
                    "—", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1
            );
            invoice.addLineItem(noteLine);
        }

        invoiceRepo.save(invoice);
        invoice.markIssued();

        // Update schedule state
        schedule.accumulateHours(billed);
        schedule.markRan(java.time.Instant.now());
        scheduleRepo.save(schedule);

        log.info("Logged {}h (billed {}h) for schedule={} invoice={} period={}",
                req.actualHours(), billed, scheduleId, invoiceNumber, req.periodLabel());

        if (minimised) {
            log.warn("Minimum hours applied: worked={} billed={} minimum={}",
                    req.actualHours(), billed, schedule.getMinimumHoursPerCycle());
        }

        return invoiceQueryService.getInvoice(tenantId, invoice.getId());
    }

    private RecurringSchedule findActive(TenantId tenantId, UUID id) {
        return scheduleRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id.toString()));
    }
}
