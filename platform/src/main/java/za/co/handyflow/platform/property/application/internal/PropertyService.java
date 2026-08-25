package za.co.handyflow.platform.property.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.property.domain.model.*;
import za.co.handyflow.platform.property.domain.repository.*;
import za.co.handyflow.platform.property.dto.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FIX: backlog 1.6 — createPaymentRecord()/recordPayment() now post to
 * the general ledger via AccountingFacade. The field/imports/account-
 * code constants/findAccountByCode() helper referenced by
 * postRentalRevenueJournal() were never actually added in an earlier
 * pass — that method was calling accountingFacade, AR_ACCOUNT_CODE,
 * REVENUE_ACCOUNT_CODE, and findAccountByCode(), none of which existed
 * anywhere in this file. This was a live compile error until now, not
 * a design gap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository     propertyRepository;
    private final UnitRepository         unitRepository;
    private final LeaseRepository        leaseRepository;
    private final LeasePaymentRepository paymentRepository;
    private final InspectionRepository   inspectionRepository;
    private final EmailService           emailService;
    // FIX: backlog 1.6 — was referenced by postRentalRevenueJournal()
    // but never actually declared. Direct call, no event indirection —
    // confirmed no circular dependency between property and accounting.
    private final AccountingFacade       accountingFacade;

    private static final String AR_ACCOUNT_CODE      = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";

    // ── Properties ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PropertyResponse> getProperties(TenantId tenantId, Pageable pageable) {
        Map<UUID, long[]> countsByProperty = propertyRepository.countUnitsByProperty(tenantId).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> new long[]{
                                ((Number) row[1]).longValue(),
                                ((Number) row[2]).longValue(),
                                ((Number) row[3]).longValue()
                        }));

        return propertyRepository.findAllActive(tenantId, pageable)
                .map(p -> {
                    long[] counts = countsByProperty.getOrDefault(p.getId(), new long[]{0L, 0L, 0L});
                    return toPropertyResponse(p, List.of(), counts[0], counts[1], counts[2]);
                });
    }

    @Transactional(readOnly = true)
    public PropertyResponse getProperty(TenantId tenantId, UUID id) {
        Property property = propertyRepository
                .findActiveByIdWithUnits(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id.toString()));

        List<UnitResponse> units = property.getUnits().stream()
                .filter(u -> !u.isDeleted())
                .map(this::toUnitResponse)
                .toList();

        return toPropertyResponse(property, units);
    }

    @Transactional
    public PropertyResponse createProperty(TenantId tenantId, CreatePropertyRequest req) {
        Property property = Property.create(tenantId, req.name(), req.propertyType(),
                req.address(), req.description(), req.customerId(),
                req.purchasePrice(), req.marketValue());
        propertyRepository.save(property);
        log.info("Created property={} tenant={}", property.getName(), tenantId);
        return toPropertyResponse(property, List.of());
    }

    @Transactional
    public void deleteProperty(TenantId tenantId, UUID id) {
        Property property = propertyRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id.toString()));
        boolean hasActiveLease = property.getUnits().stream()
                .anyMatch(u -> leaseRepository.findActiveLease(u.getId()).isPresent());
        if (hasActiveLease)
            throw new HandyFlowException(
                    "Cannot delete a property with active leases. Terminate all leases first.",
                    HttpStatus.CONFLICT, "PROPERTY_HAS_ACTIVE_LEASES");
        property.softDelete(null);
        propertyRepository.save(property);
    }

    // ── Units ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UnitResponse> getUnits(TenantId tenantId, UUID propertyId,
                                       String status, Pageable pageable) {
        if (propertyId != null)
            return unitRepository.findByProperty(propertyId, pageable).map(this::toUnitResponse);
        if (status != null)
            return unitRepository.findByStatus(tenantId, status.toUpperCase(), pageable).map(this::toUnitResponse);
        return unitRepository.findAllActive(tenantId, pageable).map(this::toUnitResponse);
    }

    @Transactional
    public UnitResponse addUnit(TenantId tenantId, UUID propertyId, CreateUnitRequest req) {
        Property property = propertyRepository.findActiveById(tenantId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId.toString()));

        if (unitRepository.existsByPropertyIdAndUnitNumberAndDeletedAtIsNull(
                propertyId, req.unitNumber().toUpperCase()))
            throw new IllegalArgumentException(
                    "Unit number " + req.unitNumber() + " already exists in this property");

        Unit unit = Unit.create(tenantId, property, req.unitNumber(), req.unitType(),
                req.floorNumber(), req.sizeSqm(), req.baseRent(),
                req.depositAmount(), req.furnished());
        unitRepository.save(unit);
        return toUnitResponse(unit);
    }

    // ── Leases ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<LeaseResponse> getLeases(TenantId tenantId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? leaseRepository.findAllActive(tenantId, pageable)
                : leaseRepository.findByStatus(tenantId, status.toUpperCase(), pageable);
        return page.map(this::toLeaseResponse);
    }

    @Transactional(readOnly = true)
    public LeaseResponse getLease(TenantId tenantId, UUID id) {
        return leaseRepository.findActiveById(tenantId, id)
                .map(this::toLeaseResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", id.toString()));
    }

    @Transactional
    public LeaseResponse createLease(TenantId tenantId, UUID unitId, CreateLeaseRequest req) {
        Unit unit = unitRepository.findActiveById(tenantId, unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", unitId.toString()));

        leaseRepository.findActiveLease(unitId).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Unit already has an active lease. Terminate it before creating a new one.");
        });

        Lease lease = Lease.create(tenantId, unitId, req.customerId(),
                req.lesseeName(), req.lesseeIdNumber(), req.lesseeEmail(),
                req.lesseePhone(), req.startDate(), req.endDate(),
                req.monthlyRent(), req.depositAmount(), req.paymentDay(),
                req.escalationRate());
        leaseRepository.save(lease);

        unit.occupy();
        unitRepository.save(unit);

        if (req.lesseeEmail() != null && !req.lesseeEmail().isBlank()) {
            emailService.send(
                    req.lesseeEmail(),
                    "Your lease agreement — " + unit.getProperty().getName(),
                    EmailTemplates.leaseCreated(
                            req.lesseeName(),
                            unit.getProperty().getName(),
                            unit.getUnitNumber(),
                            req.startDate().toString(),
                            req.endDate() != null ? req.endDate().toString() : "Month-to-month",
                            req.monthlyRent().toString(),
                            req.paymentDay()));
        }

        log.info("Lease created unit={} lessee={}", unitId, req.lesseeName());
        return toLeaseResponse(lease);
    }

    @Transactional
    public LeaseResponse terminateLease(TenantId tenantId, UUID leaseId, String reason) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        lease.terminate(reason);
        leaseRepository.save(lease);

        unitRepository.findActiveById(tenantId, lease.getUnitId()).ifPresent(unit -> {
            unit.vacate();
            unitRepository.save(unit);
        });

        if (lease.getLesseeEmail() != null && !lease.getLesseeEmail().isBlank()) {
            emailService.send(
                    lease.getLesseeEmail(),
                    "Lease termination notice",
                    EmailTemplates.leaseTerminated(lease.getLesseeName(), reason));
        }

        return toLeaseResponse(lease);
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(TenantId tenantId, UUID leaseId, Pageable pageable) {
        leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));
        return paymentRepository.findByLease(leaseId, pageable).map(this::toPaymentResponse);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getOutstandingPayments(TenantId tenantId) {
        return paymentRepository.findOutstanding(tenantId).stream()
                .map(this::toPaymentResponse).toList();
    }

    @Transactional
    public PaymentResponse createPaymentRecord(TenantId tenantId, UUID leaseId,
                                               CreatePaymentRequest req) {
        leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        paymentRepository.findByPeriod(leaseId, req.periodYear(), req.periodMonth())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Payment record already exists for " + req.periodYear() + "/" + req.periodMonth());
                });

        LeasePayment payment = LeasePayment.create(tenantId, leaseId,
                req.periodYear(), req.periodMonth(), req.amountDue(), req.dueDate());
        paymentRepository.save(payment);

        // FIX: backlog 1.6 — was previously nothing here; rental revenue
        // for this billing period never reached the general ledger.
        postRentalRevenueJournal(tenantId, payment, req.amountDue());

        return toPaymentResponse(payment);
    }

    @Transactional
    public PaymentResponse recordPayment(TenantId tenantId, UUID leaseId,
                                         UUID paymentId, RecordPaymentRequest req) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        LeasePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.toString()));

        payment.recordPayment(req.amountPaid(), req.paidDate(), req.paymentMethod(), req.reference());
        paymentRepository.save(payment);

        // FIX: backlog 1.6 — was previously nothing here; a rent payment
        // never reached the general ledger.
        postRentPaymentJournal(tenantId, payment, req.amountPaid(), req.bankAccountId());

        log.info("Payment recorded lease={} amount={} status={}", leaseId, req.amountPaid(), payment.getStatus());

        if ("PAID".equals(payment.getStatus()) && lease.getLesseeEmail() != null) {
            emailService.send(
                    lease.getLesseeEmail(),
                    "Payment receipt — " + monthName(payment.getPeriodMonth()) + " " + payment.getPeriodYear(),
                    EmailTemplates.rentReceipt(
                            lease.getLesseeName(),
                            req.amountPaid().toString(),
                            monthName(payment.getPeriodMonth()) + " " + payment.getPeriodYear(),
                            req.paidDate().toString(),
                            req.reference()));
        }

        return toPaymentResponse(payment);
    }

    /**
     * FIX: backlog 1.6. Same "bankAccountId absent → log and skip,
     * never guess" treatment already applied everywhere else this
     * session.
     */
    private void postRentPaymentJournal(TenantId tenantId, LeasePayment payment,
                                        BigDecimal amountPaid, UUID bankAccountId) {
        try {
            if (bankAccountId == null) {
                log.warn("Rent payment recorded for payment={} tenant={} with no bankAccountId — " +
                                "cannot post a directed payment journal without knowing which account received the funds.",
                        payment.getId(), tenantId);
                return;
            }
            Optional<UUID> bankGl = accountingFacade.resolveBankAccountGL(tenantId, bankAccountId);
            if (bankGl.isEmpty()) {
                log.warn("Bank account={} for tenant={} not found or not linked — payment={} not posted",
                        bankAccountId, tenantId, payment.getId());
                return;
            }
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                log.warn("Chart of Accounts missing AR ({}) for tenant={} — payment not posted", AR_ACCOUNT_CODE, tenantId);
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            bankGl.get(), "Rent received " + payment.getPeriodMonth() + "/" + payment.getPeriodYear(),
                            amountPaid, null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Rent received " + payment.getPeriodMonth() + "/" + payment.getPeriodYear(),
                            null, amountPaid));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Rent payment received: payment " + payment.getId(),
                    payment.getId().toString(), "PAYMENT", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted rent payment journal for payment={} tenant={}", payment.getId(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post rent payment journal for payment={} tenant={}: {}",
                    payment.getId(), tenantId, e.getMessage(), e);
        }
    }

    // ── Inspections ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InspectionResponse> getInspections(TenantId tenantId, UUID unitId, Pageable pageable) {
        unitRepository.findActiveById(tenantId, unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", unitId.toString()));
        return inspectionRepository.findByUnit(unitId, pageable).map(this::toInspectionResponse);
    }

    @Transactional
    public InspectionResponse createInspection(TenantId tenantId, UUID unitId,
                                               CreateInspectionRequest req) {
        unitRepository.findActiveById(tenantId, unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", unitId.toString()));

        Inspection inspection = Inspection.create(tenantId, unitId, req.leaseId(),
                req.type(), req.inspectedAt(), req.inspectedBy(),
                req.overallCondition(), req.notes(), req.items());
        inspectionRepository.save(inspection);
        return toInspectionResponse(inspection);
    }

    // ── Lease lifecycle ───────────────────────────────────────────────────────

    @Transactional
    public LeaseResponse updateLease(TenantId tenantId, UUID leaseId, UpdateLeaseRequest req) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));
        if (!lease.isActive())
            throw new HandyFlowException("Only active leases can be updated",
                    HttpStatus.BAD_REQUEST, "LEASE_NOT_ACTIVE");
        lease.updateTerms(req.monthlyRent(), req.endDate(),
                req.paymentDay(), req.escalationRate(), req.notes());
        leaseRepository.save(lease);
        return toLeaseResponse(lease);
    }

    @Transactional
    public LeaseResponse renewLease(TenantId tenantId, UUID leaseId, RenewLeaseRequest req) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));
        if ("TERMINATED".equals(lease.getStatus()))
            throw new HandyFlowException("Terminated leases cannot be renewed. Create a new lease instead.",
                    HttpStatus.BAD_REQUEST, "LEASE_TERMINATED");

        BigDecimal newRent = req.newMonthlyRent();
        if (newRent == null && lease.getEscalationRate() != null
                && lease.getEscalationRate().compareTo(BigDecimal.ZERO) > 0) {
            newRent = lease.getMonthlyRent()
                    .multiply(BigDecimal.ONE.add(
                            lease.getEscalationRate().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        lease.renew(req.newEndDate(), newRent, req.newEscalationRate());
        leaseRepository.save(lease);

        unitRepository.findActiveById(tenantId, lease.getUnitId()).ifPresent(unit -> {
            if (!unit.isOccupied()) { unit.occupy(); unitRepository.save(unit); }
        });

        if (lease.getLesseeEmail() != null) {
            emailService.send(
                    lease.getLesseeEmail(),
                    "Your lease has been renewed",
                    EmailTemplates.leaseRenewed(
                            lease.getLesseeName(),
                            req.newEndDate().toString(),
                            newRent != null ? newRent.toString() : lease.getMonthlyRent().toString()));
        }

        log.info("Renewed lease={} new end={} new rent={}", leaseId, req.newEndDate(), newRent);
        return toLeaseResponse(lease);
    }

    @Transactional
    public LeaseResponse escalateLease(TenantId tenantId, UUID leaseId, EscalateLeaseRequest req) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));
        if (!lease.isActive())
            throw new HandyFlowException("Only active leases can be escalated",
                    HttpStatus.BAD_REQUEST, "LEASE_NOT_ACTIVE");

        BigDecimal oldRent = lease.getMonthlyRent();

        if (req.newMonthlyRent() != null) {
            lease.setMonthlyRent(req.newMonthlyRent());
        } else if (req.escalationPercent() != null) {
            BigDecimal newRent = lease.applyEscalation(req.escalationPercent());
            log.info("Escalated lease={} from R{} to R{} ({}%)", leaseId, oldRent, newRent, req.escalationPercent());
        } else {
            throw new HandyFlowException("Provide either escalationPercent or newMonthlyRent",
                    HttpStatus.BAD_REQUEST, "MISSING_ESCALATION");
        }

        leaseRepository.save(lease);

        if (lease.getLesseeEmail() != null) {
            emailService.send(
                    lease.getLesseeEmail(),
                    "Rent escalation notice",
                    EmailTemplates.rentEscalation(
                            lease.getLesseeName(),
                            oldRent.toString(),
                            lease.getMonthlyRent().toString(),
                            LocalDate.now().plusMonths(1).withDayOfMonth(1).toString()));
        }

        return toLeaseResponse(lease);
    }

    // ── GL posting helpers ───────────────────────────────────────────────────

    /**
     * FIX: backlog 1.6. See createPaymentRecord()'s own call-site
     * comment for the full rationale. No VAT line — nothing in
     * CreatePaymentRequest suggests rental income here carries VAT, and
     * rental income is commonly VAT-exempt under South African law;
     * fabricating a VAT split with no evidence would be a worse error
     * than omitting it.
     */
    private void postRentalRevenueJournal(TenantId tenantId, LeasePayment payment, BigDecimal amountDue) {
        try {
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — payment record={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId, payment.getId());
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Rent due " + payment.getPeriodMonth() + "/" + payment.getPeriodYear(),
                            amountDue, null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            revenueAccountId, "Rental income " + payment.getPeriodMonth() + "/" + payment.getPeriodYear(),
                            null, amountDue));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Rent due: " + payment.getPeriodMonth() + "/" + payment.getPeriodYear(),
                    payment.getId().toString(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted rental revenue journal for payment={} tenant={}", payment.getId(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post rental revenue journal for payment={} tenant={}: {}",
                    payment.getId(), tenantId, e.getMessage(), e);
        }
    }

    /**
     * FIX: backlog 1.6 — was referenced by postRentalRevenueJournal()
     * (already present from an earlier pass) but never actually
     * defined anywhere in this file — a live "cannot resolve method"
     * compile error until this was added.
     */
    private UUID findAccountByCode(TenantId tenantId, String code) {
        return accountingFacade.getAccounts(tenantId).stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private PropertyResponse toPropertyResponse(Property p, List<UnitResponse> units) {
        long vacant   = units.stream().filter(u -> "VACANT".equals(u.status())).count();
        long occupied = units.stream().filter(u -> "OCCUPIED".equals(u.status())).count();
        return toPropertyResponse(p, units, units.size(), vacant, occupied);
    }

    private PropertyResponse toPropertyResponse(Property p, List<UnitResponse> units,
                                                long total, long vacant, long occupied) {
        return new PropertyResponse(p.getId(), p.getName(), p.getPropertyType(), p.getAddress(),
                p.getDescription(), p.getCustomerId(), p.getPurchasePrice(), p.getMarketValue(),
                (int) total, vacant, occupied, units, p.getCreatedAt());
    }

    private UnitResponse toUnitResponse(Unit u) {
        return new UnitResponse(u.getId(), u.getProperty().getId(), u.getUnitNumber(),
                u.getUnitType(), u.getFloorNumber(), u.getSizeSqm(), u.getBaseRent(),
                u.getDepositAmount(), u.getStatus(), u.isFurnished(), u.getAmenities(),
                u.getNotes(), u.getCreatedAt());
    }

    private LeaseResponse toLeaseResponse(Lease l) {
        return new LeaseResponse(l.getId(), l.getUnitId(), l.getCustomerId(),
                l.getLesseeName(), l.getLesseeEmail(), l.getLesseePhone(),
                l.getStartDate(), l.getEndDate(), l.getMonthlyRent(), l.getDepositAmount(),
                l.isDepositPaid(), l.getPaymentDay(), l.getEscalationRate(), l.getStatus(),
                l.isMonthToMonth(), l.isExpiringSoon(), l.getCreatedAt());
    }

    private PaymentResponse toPaymentResponse(LeasePayment p) {
        return new PaymentResponse(p.getId(), p.getLeaseId(), p.getPeriodYear(), p.getPeriodMonth(),
                p.getAmountDue(), p.getAmountPaid(), p.getBalance(), p.getDueDate(), p.getPaidDate(),
                p.getPaymentMethod(), p.getReference(), p.getStatus(), p.getCreatedAt());
    }

    private InspectionResponse toInspectionResponse(Inspection i) {
        return new InspectionResponse(i.getId(), i.getUnitId(), i.getLeaseId(), i.getType(),
                i.getInspectedAt(), i.getInspectedBy(), i.getOverallCondition(),
                i.getNotes(), i.getItems(), i.getCreatedAt());
    }

    private static String monthName(int m) {
        return java.time.Month.of(m).getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
    }
}