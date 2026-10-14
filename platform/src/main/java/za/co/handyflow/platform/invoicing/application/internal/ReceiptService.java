package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.invoicing.ReceiptRecordedEvent;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.ReceiptDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Money received from customers, how it settles invoices, and customer credit (finance patch F8, 2026-10-14).
 * <p>
 * A receipt is posted to the ledger once, when it is recorded (ReceiptRecordedEvent: Dr bank, Cr Accounts Receivable).
 * Allocating it to invoices, now or later, only changes which invoices are settled. Credit from a credit note (the part
 * beyond what its invoice still owed) is a receipt too, with no money and no posting: the credit note already posted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final JdbcTemplate jdbc;
    private final InvoiceRepository invoiceRepo;
    private final TenantNumberingFacade numbering;
    private final ApplicationEventPublisher events;

    // ── Recording money ───────────────────────────────────────────────────────

    /** Money from a customer, settling the invoices given (or the oldest first), with the rest kept as their credit. */
    @Transactional
    public ReceiptResponse receive(TenantId tenantId, ReceiveMoneyRequest req, UUID by) {
        if (req == null || req.customerId() == null)
            throw bad("Choose the customer the money came from", "CUSTOMER_REQUIRED");
        BigDecimal amount = positive(req.amount());
        LocalDate on = receivedOn(req.receivedOn());
        List<Invoice> open = invoiceRepo.findOpenForCustomer(tenantId, req.customerId());
        List<ReceiptRules.Allocation> allocations = req.allocations() != null && !req.allocations().isEmpty()
                ? toAllocations(req.allocations())
                : Boolean.TRUE.equals(req.autoAllocate())
                    ? ReceiptRules.oldestFirst(amount, open.stream().map(i -> new ReceiptRules.Open(i.getId(), i.getBalanceDue())).toList())
                    : List.of();
        requireAllocatable(allocations, amount, open);

        UUID id = insert(tenantId, req.customerId(), "PAYMENT", null, amount, on, req.method(), req.reference(),
                req.bankAccountId(), req.notes(), by);
        String applied = apply(tenantId, id, allocations, open, on, by);
        publish(tenantId, id, req.customerId(), amount, req.bankAccountId(), on, req.reference(), applied);
        return get(tenantId, id);
    }

    /**
     * A payment recorded against one invoice (the invoice's own Record payment). For a customer, anything above what the
     * invoice owes stays on their account; a walk-in invoice cannot hold credit, so it is refused.
     */
    @Transactional
    public ReceiptResponse receiveForInvoice(TenantId tenantId, Invoice invoice, BigDecimal amountPaid, String method,
                                             String reference, UUID bankAccountId, Instant paidAt, UUID by) {
        BigDecimal amount = positive(amountPaid);
        BigDecimal owed = invoice.getBalanceDue();
        if (invoice.getCustomerId() == null && amount.compareTo(owed.add(new BigDecimal("0.005"))) > 0)
            throw bad("That is more than the R " + owed + " still owing, and a walk-in client has no account to hold the rest",
                    "MORE_THAN_OWING");
        LocalDate on = paidAt != null ? paidAt.atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();
        UUID id = insert(tenantId, invoice.getCustomerId(), "PAYMENT", null, amount, on, method, reference, bankAccountId, null, by);
        BigDecimal toInvoice = ReceiptRules.money(amount.min(owed));
        String applied = "";
        if (toInvoice.signum() > 0) {
            invoice.allocatePayment(toInvoice, paidAt != null ? paidAt : Instant.now());
            invoiceRepo.save(invoice);
            insertAllocation(tenantId, id, invoice.getId(), toInvoice, by);
            applied = invoice.getInvoiceNumber();
        }
        publish(tenantId, id, invoice.getCustomerId(), amount, bankAccountId, on, reference, applied);
        return get(tenantId, id);
    }

    /** Uses a receipt's remaining credit to settle more of the same customer's invoices. */
    @Transactional
    public ReceiptResponse allocate(TenantId tenantId, UUID receiptId, AllocateRequest req, UUID by) {
        Row r = row(tenantId, receiptId, true);
        if (r.customerId == null) throw bad("This receipt has no customer account to use", "NO_CUSTOMER");
        List<ReceiptRules.Allocation> allocations = toAllocations(req != null ? req.allocations() : null);
        if (allocations.isEmpty()) throw bad("Say which invoices to settle and how much", "NOTHING_TO_ALLOCATE");
        List<Invoice> open = invoiceRepo.findOpenForCustomer(tenantId, r.customerId);
        requireAllocatable(allocations, r.amount.subtract(r.allocated), open);
        apply(tenantId, receiptId, allocations, open, LocalDate.now(), by);
        log.info("[Invoicing] Receipt {} allocated to {} invoice(s) by={}", r.number, allocations.size(), by);
        return get(tenantId, receiptId);
    }

    /** Puts the part of a credit note beyond what its invoice still owed on the customer's account (no posting). */
    @Transactional
    public UUID creditFromCreditNote(TenantId tenantId, UUID customerId, UUID creditNoteId, String creditNoteNumber,
                                     BigDecimal amount, UUID by) {
        if (customerId == null || amount == null || amount.signum() <= 0) return null;
        return insert(tenantId, customerId, "CREDIT_NOTE", creditNoteId, ReceiptRules.money(amount), LocalDate.now(), null,
                creditNoteNumber, null, "From credit note " + creditNoteNumber, by);
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReceiptResponse get(TenantId tenantId, UUID id) {
        Row r = row(tenantId, id, false);
        List<AllocationResponse> allocations = jdbc.query("""
                SELECT a.invoice_id, i.invoice_number, a.amount, a.allocated_at
                  FROM receipt_allocations a JOIN invoices i ON i.id = a.invoice_id
                 WHERE a.receipt_id = ? AND a.tenant_id = ? ORDER BY a.allocated_at""",
                (rs, n) -> new AllocationResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                        rs.getTimestamp(4).toInstant()), id, tenantId.getValue());
        return r.toResponse(allocations);
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> forCustomer(TenantId tenantId, UUID customerId) {
        return jdbc.query("SELECT id FROM customer_receipts WHERE tenant_id = ? AND customer_id = ? ORDER BY received_on DESC, created_at DESC LIMIT 200",
                (rs, n) -> rs.getObject(1, UUID.class), tenantId.getValue(), customerId)
                .stream().map(id -> get(tenantId, id)).toList();
    }

    /** What a customer owes, their open invoices (oldest first) and the credit they have to use. */
    @Transactional(readOnly = true)
    public CustomerAccount account(TenantId tenantId, UUID customerId) {
        List<OpenInvoice> open = invoiceRepo.findOpenForCustomer(tenantId, customerId).stream()
                .map(i -> new OpenInvoice(i.getId(), i.getInvoiceNumber(), i.getDueDate(), i.getTotal(), i.getAmountPaid(),
                        i.getCreditedAmount(), i.getBalanceDue(), i.getStatus().name()))
                .filter(o -> o.balanceDue().signum() > 0)
                .toList();
        List<ReceiptResponse> credit = jdbc.query("""
                SELECT id FROM customer_receipts WHERE tenant_id = ? AND customer_id = ? AND allocated < amount
                 ORDER BY received_on, created_at""", (rs, n) -> rs.getObject(1, UUID.class), tenantId.getValue(), customerId)
                .stream().map(id -> get(tenantId, id)).toList();
        BigDecimal owed = open.stream().map(OpenInvoice::balanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = credit.stream().map(ReceiptResponse::available).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CustomerAccount(customerId, ReceiptRules.money(owed), ReceiptRules.money(available), open, credit);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void requireAllocatable(List<ReceiptRules.Allocation> allocations, BigDecimal available, List<Invoice> open) {
        Map<UUID, BigDecimal> balances = open.stream().collect(Collectors.toMap(Invoice::getId, Invoice::getBalanceDue, (a, b) -> a));
        String problem = ReceiptRules.allocationProblem(allocations, available, balances);
        if (problem != null) throw bad(problem, "ALLOCATION_REFUSED");
    }

    /** Settles the invoices and records the allocations; returns the invoice numbers settled, for the ledger memo. */
    private String apply(TenantId tenantId, UUID receiptId, List<ReceiptRules.Allocation> allocations, List<Invoice> open,
                         LocalDate on, UUID by) {
        Map<UUID, Invoice> byId = open.stream().collect(Collectors.toMap(Invoice::getId, i -> i, (a, b) -> a));
        List<String> numbers = new ArrayList<>();
        Instant at = on.atStartOfDay(ZoneId.systemDefault()).toInstant();
        for (ReceiptRules.Allocation a : allocations) {
            Invoice inv = byId.get(a.invoiceId());
            inv.allocatePayment(ReceiptRules.money(a.amount()), at);
            invoiceRepo.save(inv);
            insertAllocation(tenantId, receiptId, inv.getId(), ReceiptRules.money(a.amount()), by);
            numbers.add(inv.getInvoiceNumber());
        }
        return String.join(", ", numbers);
    }

    private UUID insert(TenantId tenantId, UUID customerId, String kind, UUID sourceId, BigDecimal amount, LocalDate on,
                        String method, String reference, UUID bankAccountId, String notes, UUID by) {
        UUID id = UUID.randomUUID();
        String number = numbering.next(tenantId, "RECEIPT", "RC");
        jdbc.update("""
                INSERT INTO customer_receipts (id, tenant_id, receipt_number, customer_id, kind, source_id, amount, received_on,
                                               method, reference, bank_account_id, notes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, tenantId.getValue(), number, customerId, kind, sourceId, amount, Date.valueOf(on),
                trim(method, 30), trim(reference, 100), bankAccountId, trim(notes, 500), by);
        log.info("[Invoicing] Receipt {} {} R {} customer={} tenant={} by={}", number, kind, amount, customerId, tenantId, by);
        return id;
    }

    private void insertAllocation(TenantId tenantId, UUID receiptId, UUID invoiceId, BigDecimal amount, UUID by) {
        jdbc.update("INSERT INTO receipt_allocations (tenant_id, receipt_id, invoice_id, amount, allocated_by) VALUES (?, ?, ?, ?, ?)",
                tenantId.getValue(), receiptId, invoiceId, amount, by);
        int updated = jdbc.update("UPDATE customer_receipts SET allocated = allocated + ? WHERE id = ? AND tenant_id = ? AND allocated + ? <= amount",
                amount, receiptId, tenantId.getValue(), amount);
        if (updated != 1) throw bad("This receipt does not have that much credit left", "OVER_ALLOCATED");
    }

    private void publish(TenantId tenantId, UUID receiptId, UUID customerId, BigDecimal amount, UUID bankAccountId,
                         LocalDate on, String reference, String invoiceNumbers) {
        String number = jdbc.queryForObject("SELECT receipt_number FROM customer_receipts WHERE id = ?", String.class, receiptId);
        events.publishEvent(ReceiptRecordedEvent.of(tenantId, receiptId, number, customerId, amount, bankAccountId, on,
                reference, invoiceNumbers));
    }

    private record Row(UUID id, String number, UUID customerId, String kind, BigDecimal amount, BigDecimal allocated,
                       LocalDate receivedOn, String method, String reference, UUID bankAccountId, String notes, Instant createdAt) {
        ReceiptResponse toResponse(List<AllocationResponse> allocations) {
            return new ReceiptResponse(id, number, customerId, kind, amount, allocated, ReceiptRules.money(amount.subtract(allocated)),
                    receivedOn, method, reference, bankAccountId, notes, createdAt, allocations);
        }
    }

    private Row row(TenantId tenantId, UUID id, boolean forUpdate) {
        return jdbc.query("""
                SELECT id, receipt_number, customer_id, kind, amount, allocated, received_on, method, reference, bank_account_id,
                       notes, created_at FROM customer_receipts WHERE id = ? AND tenant_id = ?""" + (forUpdate ? " FOR UPDATE" : ""),
                (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getDate(7).toLocalDate(), rs.getString(8), rs.getString(9),
                        rs.getObject(10, UUID.class), rs.getString(11), rs.getTimestamp(12).toInstant()),
                id, tenantId.getValue()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", id.toString()));
    }

    private static List<ReceiptRules.Allocation> toAllocations(List<AllocationInput> in) {
        if (in == null) return List.of();
        return in.stream().map(a -> new ReceiptRules.Allocation(a == null ? null : a.invoiceId(), a == null ? null : a.amount())).toList();
    }

    private static BigDecimal positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw bad("Enter the amount received", "AMOUNT_REQUIRED");
        return ReceiptRules.money(amount);
    }

    private static LocalDate receivedOn(LocalDate d) {
        LocalDate on = d != null ? d : LocalDate.now();
        if (on.isAfter(LocalDate.now())) throw bad("The date received cannot be in the future", "FUTURE_DATE");
        return on;
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }
}
