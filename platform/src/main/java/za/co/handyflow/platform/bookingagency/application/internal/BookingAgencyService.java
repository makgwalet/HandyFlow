package za.co.handyflow.platform.bookingagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.bookingagency.domain.model.*;
import za.co.handyflow.platform.bookingagency.domain.repository.*;
import za.co.handyflow.platform.bookingagency.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Foundation layer only — practice profile and client portfolio CRUD.
 * Bookable resources, the slot/booking engine, and billing are separate,
 * later services — see this module's own package-info.java for the
 * full planned layer list and the open billing-model question.
 * <p>
 * FIX: backlog 1.6 — generateInvoice()/recordPayment() now post to the
 * general ledger via AccountingFacade. Same proven pattern already
 * applied to Invoicing/Clinic: revenue recognized at invoice
 * generation, payment posted with a bankAccountId-optional gate (logs
 * and skips rather than guessing a default account when absent).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingAgencyService {

    private final BookAgencyProfileRepository profileRepo;
    private final BookAgencyClientRepository clientRepo;
    private final BookPortalAccessGrantRepository portalGrantRepo;
    private final za.co.handyflow.platform.shared.EmailService emailService;
    private final BookAgencyResourceRepository resourceRepo;
    private final BookAgencyOfferingRepository offeringRepo;
    private final BookAgencyBookingRepository bookingRepo;
    private final za.co.handyflow.platform.shared.TenantSequenceService sequenceService;
    private final BookAgencyInvoiceRepository invoiceRepo;
    private final BookAgencyPaymentRepository paymentRepo;
    // FIX: backlog 1.6 — the shared AccountingFacade. Direct call, no
    // event indirection — confirmed no circular dependency.
    private final AccountingFacade accountingFacade;
    // FIX (VAT sweep, module 2): replaces a hardcoded
    // subtotal.multiply(new BigDecimal("0.15")) fallback below.
    private final VatRateProvider vatRateProvider;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private static final String AR_ACCOUNT_CODE      = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";
    private static final String VAT_ACCOUNT_CODE      = "2100";

    // ── Agency profile ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BookAgencyProfileResponse getProfile(TenantId tenantId) {
        BookAgencyProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("BookAgencyProfile", tenantId.getValue().toString()));
        return toProfileResponse(profile);
    }

    @Transactional
    public BookAgencyProfileResponse upsertProfile(TenantId tenantId, UpdateBookAgencyProfileRequest req) {
        BookAgencyProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseGet(() -> BookAgencyProfile.create(tenantId.getValue(), req.agencyName()));
        profile.update(req.agencyName(), req.registrationNumber(), req.email(), req.phone(),
                req.physicalAddress(), req.logoUrl());
        profileRepo.save(profile);
        log.info("Booking agency profile upserted tenant={} agencyName={}", tenantId.getValue(), req.agencyName());
        return toProfileResponse(profile);
    }

    // ── Client portfolio ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BookAgencyClientResponse> getClients(TenantId tenantId, Pageable pageable) {
        return clientRepo.findAllActive(tenantId.getValue(), pageable).map(this::toClientResponse);
    }

    @Transactional(readOnly = true)
    public BookAgencyClientResponse getClient(TenantId tenantId, UUID id) {
        return toClientResponse(findActiveClient(tenantId, id));
    }

    @Transactional
    public BookAgencyClientResponse createClient(TenantId tenantId, CreateBookAgencyClientRequest req) {
        BookAgencyClient client = BookAgencyClient.create(tenantId.getValue(), req.tradingName(),
                req.businessType(), req.timezone(), req.contactName(), req.contactEmail(), req.contactPhone());
        clientRepo.save(client);
        log.info("Booking agency client created tenant={} client={}", tenantId.getValue(), req.tradingName());
        return toClientResponse(client);
    }

    @Transactional
    public BookAgencyClientResponse updateClient(TenantId tenantId, UUID id, CreateBookAgencyClientRequest req) {
        BookAgencyClient client = findActiveClient(tenantId, id);
        client.update(req.tradingName(), req.businessType(), req.timezone(),
                req.contactName(), req.contactEmail(), req.contactPhone(), null);
        clientRepo.save(client);
        return toClientResponse(client);
    }

    @Transactional
    public BookAgencyClientResponse deactivateClient(TenantId tenantId, UUID id) {
        BookAgencyClient client = findActiveClient(tenantId, id);
        if ("INACTIVE".equals(client.getStatus())) {
            throw new HandyFlowException("Client is already inactive", HttpStatus.BAD_REQUEST, "ALREADY_INACTIVE");
        }
        client.deactivate();
        clientRepo.save(client);
        log.info("Booking agency client deactivated tenant={} client={}", tenantId.getValue(), id);
        return toClientResponse(client);
    }

    @Transactional
    public BookAgencyClientResponse reactivateClient(TenantId tenantId, UUID id) {
        BookAgencyClient client = findActiveClient(tenantId, id);
        client.reactivate();
        clientRepo.save(client);
        return toClientResponse(client);
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID id) {
        BookAgencyClient client = findActiveClient(tenantId, id);
        client.softDelete();
        clientRepo.save(client);
        log.info("Booking agency client deleted tenant={} client={}", tenantId.getValue(), id);
    }

    @Transactional
    public PortalAccessGrantResponse invitePortalUser(TenantId tenantId, UUID clientId, String email, UUID invitedBy) {
        BookAgencyClient client = findActiveClient(tenantId, clientId);

        boolean alreadyGranted = portalGrantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email) && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException("This email already has a pending or active invite for this client",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        BookPortalAccessGrant grant = BookPortalAccessGrant.createInvite(tenantId.getValue(), clientId, email, invitedBy);
        portalGrantRepo.save(grant);

        emailService.send(email, client.getTradingName() + " has invited you to their booking portal",
                za.co.handyflow.platform.shared.EmailTemplates.portalInvite(
                        client.getTradingName(), "Booking Agency",
                        frontendUrl + "/booking-agency/portal/auth/accept-invite?token=" + grant.getInviteToken()));

        log.info("Booking agency portal invite sent: {} -> client={}", email, clientId);
        return toGrantResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<PortalAccessGrantResponse> getPortalAccessGrants(TenantId tenantId, UUID clientId) {
        findActiveClient(tenantId, clientId);
        return portalGrantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .map(this::toGrantResponse).toList();
    }

    @Transactional
    public PortalAccessGrantResponse revokePortalAccess(TenantId tenantId, UUID clientId, UUID grantId, UUID revokedBy) {
        BookPortalAccessGrant grant = portalGrantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("PortalAccessGrant", grantId.toString()));
        if (!grant.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("PortalAccessGrant", grantId.toString());
        }
        grant.revoke(revokedBy);
        portalGrantRepo.save(grant);
        return toGrantResponse(grant);
    }

    @Transactional
    public ResourceResponse createResource(TenantId tenantId, CreateResourceRequest req) {
        findActiveClient(tenantId, req.clientId()); // ownership check
        BookAgencyResource resource = BookAgencyResource.create(tenantId.getValue(), req.clientId(),
                req.name(), req.roleDescription(), req.workingHoursStart(), req.workingHoursEnd());
        resourceRepo.save(resource);
        return toResourceResponse(resource);
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> getResourcesForClient(TenantId tenantId, UUID clientId) {
        findActiveClient(tenantId, clientId);
        return resourceRepo.findActiveByClient(clientId).stream().map(this::toResourceResponse).toList();
    }

    @Transactional
    public ResourceResponse updateResource(TenantId tenantId, UUID id, CreateResourceRequest req) {
        BookAgencyResource resource = findResource(tenantId, id);
        resource.update(req.name(), req.roleDescription(), req.workingHoursStart(), req.workingHoursEnd());
        resourceRepo.save(resource);
        return toResourceResponse(resource);
    }

    @Transactional
    public ResourceResponse deactivateResource(TenantId tenantId, UUID id) {
        BookAgencyResource resource = findResource(tenantId, id);
        resource.deactivate();
        resourceRepo.save(resource);
        return toResourceResponse(resource);
    }

    @Transactional
    public ResourceResponse reactivateResource(TenantId tenantId, UUID id) {
        BookAgencyResource resource = findResource(tenantId, id);
        resource.reactivate();
        resourceRepo.save(resource);
        return toResourceResponse(resource);
    }

    @Transactional
    public OfferingResponse createOffering(TenantId tenantId, CreateOfferingRequest req) {
        findActiveClient(tenantId, req.clientId());
        BookAgencyOffering offering = BookAgencyOffering.create(tenantId.getValue(), req.clientId(),
                req.name(), req.durationMinutes(), req.bufferMinutes(), req.price());
        offeringRepo.save(offering);
        return toOfferingResponse(offering);
    }

    @Transactional(readOnly = true)
    public List<OfferingResponse> getOfferingsForClient(TenantId tenantId, UUID clientId) {
        findActiveClient(tenantId, clientId);
        return offeringRepo.findActiveByClient(clientId).stream().map(this::toOfferingResponse).toList();
    }

    @Transactional
    public OfferingResponse updateOffering(TenantId tenantId, UUID id, CreateOfferingRequest req) {
        BookAgencyOffering offering = findOffering(tenantId, id);
        offering.update(req.name(), req.durationMinutes(), req.bufferMinutes(), req.price());
        offeringRepo.save(offering);
        return toOfferingResponse(offering);
    }

    @Transactional
    public OfferingResponse deactivateOffering(TenantId tenantId, UUID id) {
        BookAgencyOffering offering = findOffering(tenantId, id);
        offering.deactivate();
        offeringRepo.save(offering);
        return toOfferingResponse(offering);
    }


    /**
     * SCOPE NOTE: checks for overlapping CONFIRMED bookings on the same
     * resource. Deliberately does NOT validate against the resource's
     * working hours (e.g. rejecting a booking outside 9am-5pm) — that
     * needs day-of-week-aware logic this layer doesn't have yet. Real,
     * flagged gap, not silently assumed handled.
     */
    @Transactional
    public BookingResponse createBooking(TenantId tenantId, UUID clientId, CreateBookingRequest req) {
        findActiveClient(tenantId, clientId);
        BookAgencyResource resource = findResource(tenantId, req.resourceId());
        if (!resource.isActive()) {
            throw new HandyFlowException("This resource is not active", HttpStatus.BAD_REQUEST, "RESOURCE_INACTIVE");
        }
        BookAgencyOffering offering = findOffering(tenantId, req.offeringId());
        if (!offering.isActive()) {
            throw new HandyFlowException("This offering is not active", HttpStatus.BAD_REQUEST, "OFFERING_INACTIVE");
        }

        LocalDateTime start = req.startDatetime();
        LocalDateTime end = start.plusMinutes(offering.getDurationMinutes() + offering.getBufferMinutes());

        boolean hasConflict = bookingRepo.findConfirmedCandidatesForOverlapCheck(req.resourceId(), end).stream()
                .anyMatch(existing -> existing.overlaps(start, end));
        if (hasConflict) {
            throw new HandyFlowException("This resource is already booked during that time",
                    HttpStatus.CONFLICT, "SLOT_CONFLICT");
        }

        String bookingNumber = "BK" + String.format("%05d",
                sequenceService.nextValue(tenantId, "BOOKINGAGENCY_BOOKING:" + clientId));

        BookAgencyBooking booking = BookAgencyBooking.create(tenantId.getValue(), clientId,
                req.resourceId(), req.offeringId(), bookingNumber, req.customerName(),
                req.customerPhone(), req.customerEmail(), start, end, req.notes());
        bookingRepo.save(booking);
        log.info("Booking created tenant={} client={} customer={} resource={} start={}",
                tenantId.getValue(), clientId, req.customerName(), resource.getName(), start);
        return toBookingResponse(booking, resource.getName(), offering.getName());
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        findActiveClient(tenantId, clientId);
        return bookingRepo.findByClient(clientId, pageable).map(b -> {
            String resourceName = resourceRepo.findById(b.getResourceId()).map(BookAgencyResource::getName).orElse("Unknown");
            String offeringName = offeringRepo.findById(b.getOfferingId()).map(BookAgencyOffering::getName).orElse("Unknown");
            return toBookingResponse(b, resourceName, offeringName);
        });
    }

    @Transactional
    public BookingResponse cancelBooking(TenantId tenantId, UUID id) {
        BookAgencyBooking booking = findBooking(tenantId, id);
        booking.cancel();
        bookingRepo.save(booking);
        String resourceName = resourceRepo.findById(booking.getResourceId()).map(BookAgencyResource::getName).orElse("Unknown");
        String offeringName = offeringRepo.findById(booking.getOfferingId()).map(BookAgencyOffering::getName).orElse("Unknown");
        return toBookingResponse(booking, resourceName, offeringName);
    }

    @Transactional
    public BookingResponse completeBooking(TenantId tenantId, UUID id) {
        BookAgencyBooking booking = findBooking(tenantId, id);
        booking.complete();
        bookingRepo.save(booking);
        String resourceName = resourceRepo.findById(booking.getResourceId()).map(BookAgencyResource::getName).orElse("Unknown");
        String offeringName = offeringRepo.findById(booking.getOfferingId()).map(BookAgencyOffering::getName).orElse("Unknown");
        return toBookingResponse(booking, resourceName, offeringName);
    }

    @Transactional
    public BookingResponse markNoShow(TenantId tenantId, UUID id) {
        BookAgencyBooking booking = findBooking(tenantId, id);
        booking.markNoShow();
        bookingRepo.save(booking);
        String resourceName = resourceRepo.findById(booking.getResourceId()).map(BookAgencyResource::getName).orElse("Unknown");
        String offeringName = offeringRepo.findById(booking.getOfferingId()).map(BookAgencyOffering::getName).orElse("Unknown");
        return toBookingResponse(booking, resourceName, offeringName);
    }

    /**
     * Generates a retainer invoice for one billing period. Requires
     * the client to have a monthlyRetainerAmount set — rejects with a
     * clear error rather than silently invoicing zero, since that
     * would be a real billing mistake, not a graceful default.
     * Uniqueness enforced both here (clean 409) and at the database
     * level (uq_booka_invoice_client_period) — same defense-in-depth
     * pattern used for Recruitment Agency's one-invoice-per-placement
     * rule.
     */
    @Transactional
    public BookAgencyInvoiceResponse generateInvoice(TenantId tenantId, UUID clientId, GenerateRetainerInvoiceRequest req) {
        BookAgencyClient client = findActiveClient(tenantId, clientId);

        if (client.getMonthlyRetainerAmount() == null) {
            throw new HandyFlowException(
                    "This client has no monthly retainer amount set — add one before generating an invoice",
                    HttpStatus.BAD_REQUEST, "NO_RETAINER_SET");
        }
        if (invoiceRepo.existsByClientIdAndPeriodStart(clientId, req.periodStart())) {
            throw new HandyFlowException("This client has already been invoiced for this period",
                    HttpStatus.CONFLICT, "ALREADY_INVOICED");
        }

        BigDecimal subtotal = client.getMonthlyRetainerAmount();
        BigDecimal vatAmount = req.includeVat()
                ? subtotal.multiply(vatRateProvider.rateFraction()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String invoiceNumber = "BAI" + String.format("%05d",
                sequenceService.nextValue(tenantId, "BOOKINGAGENCY_INVOICE:" + clientId));

        String monthLabel = req.periodStart().getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                + " " + req.periodStart().getYear();
        String description = "Booking management retainer — " + monthLabel;

        BookAgencyInvoice invoice = BookAgencyInvoice.create(tenantId.getValue(), clientId, invoiceNumber,
                description, req.periodStart(), req.periodEnd(), req.invoiceDate(), req.dueDate(), subtotal, vatAmount);
        invoiceRepo.save(invoice);

        // FIX: backlog 1.6 — was previously nothing here; a whole
        // retainer invoice's worth of revenue never reached the general
        // ledger.
        postInvoiceRevenueJournal(tenantId, invoice, subtotal, vatAmount);

        log.info("Generated booking agency retainer invoice={} client={} period={} amount={}",
                invoiceNumber, clientId, monthLabel, invoice.getTotal());
        return toInvoiceResponse(invoice);
    }

    /**
     * FIX: backlog 1.6. See generateInvoice()'s own call-site comment.
     */
    private void postInvoiceRevenueJournal(TenantId tenantId, BookAgencyInvoice invoice,
                                           BigDecimal subtotal, BigDecimal vatAmount) {
        try {
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — invoice={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId, invoice.getId());
                return;
            }

            boolean hasVat = vatAmount != null && vatAmount.compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenantId, VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("Chart of Accounts missing VAT Output ({}) for tenant={} — invoice={} revenue not posted",
                            VAT_ACCOUNT_CODE, tenantId, invoice.getId());
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Booking agency invoice — " + invoice.getInvoiceNumber(),
                    invoice.getTotal(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Retainer revenue — " + invoice.getInvoiceNumber(),
                    null, subtotal));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + invoice.getInvoiceNumber(), null, vatAmount));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    java.time.LocalDate.now(), "Booking agency invoice: " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for booking agency invoice={} tenant={}",
                    invoice.getInvoiceNumber(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post revenue journal for invoice={} tenant={}: {}",
                    invoice.getId(), tenantId, e.getMessage(), e);
        }
    }

    @Transactional
    public BookAgencyInvoiceResponse sendInvoice(TenantId tenantId, UUID invoiceId) {
        BookAgencyInvoice invoice = invoiceRepo.findByTenantAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BookAgencyInvoice", invoiceId.toString()));
        BookAgencyClient client = clientRepo.findActiveById(tenantId.getValue(), invoice.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("BookAgencyClient", invoice.getClientId().toString()));

        invoice.markSent();
        invoiceRepo.save(invoice);

        if (client.getContactEmail() != null) {
            // Same reused feeNote()-shaped template as the sibling
            // Payroll Bureau and Recruitment Agency billing layers —
            // one template, three modules, no reason for a fourth
            // near-identical version.
            emailService.send(client.getContactEmail(),
                    "Invoice " + invoice.getInvoiceNumber(),
                    za.co.handyflow.platform.shared.EmailTemplates.feeNote(
                            client.getTradingName(), invoice.getInvoiceNumber(),
                            invoice.getTotal().toPlainString(), invoice.getDueDate().toString()));
        }
        return toInvoiceResponse(invoice);
    }

    @Transactional
    public BookAgencyInvoiceResponse recordPayment(TenantId tenantId, UUID invoiceId,
                                                   RecordBookAgencyPaymentRequest req, UUID userId, String userName) {
        BookAgencyInvoice invoice = invoiceRepo.findByTenantAndId(tenantId.getValue(), invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BookAgencyInvoice", invoiceId.toString()));

        BookAgencyPayment payment = BookAgencyPayment.create(tenantId.getValue(), invoiceId, req.amount(),
                req.paidDate(), req.method(), req.reference(), userId, userName);
        paymentRepo.save(payment);

        invoice.recordPayment(req.amount());
        invoiceRepo.save(invoice);

        // FIX: backlog 1.6 — was previously nothing here.
        postPaymentJournal(tenantId, invoice, req.amount(), req.bankAccountId());

        log.info("Recorded payment={} against booking agency invoice={} newStatus={}",
                req.amount(), invoice.getInvoiceNumber(), invoice.getStatus());
        return toInvoiceResponse(invoice);
    }

    /**
     * FIX: backlog 1.6. Same "bankAccountId absent → log and skip,
     * never guess" treatment already applied everywhere else this
     * session.
     */
    private void postPaymentJournal(TenantId tenantId, BookAgencyInvoice invoice,
                                    BigDecimal amount, UUID bankAccountId) {
        try {
            if (bankAccountId == null) {
                log.warn("Payment recorded for invoice={} tenant={} with no bankAccountId — " +
                                "cannot post a directed payment journal without knowing which account received the funds.",
                        invoice.getInvoiceNumber(), tenantId);
                return;
            }
            Optional<UUID> bankGl = accountingFacade.resolveBankAccountGL(tenantId, bankAccountId);
            if (bankGl.isEmpty()) {
                log.warn("Bank account={} for tenant={} not found or not linked — payment for invoice={} not posted",
                        bankAccountId, tenantId, invoice.getInvoiceNumber());
                return;
            }
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            if (arAccountId == null) {
                log.warn("Chart of Accounts missing AR ({}) for tenant={} — payment not posted", AR_ACCOUNT_CODE, tenantId);
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            bankGl.get(), "Payment received — " + invoice.getInvoiceNumber(), amount, null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Payment received — " + invoice.getInvoiceNumber(), null, amount));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    java.time.LocalDate.now(), "Payment received: " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "PAYMENT", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted payment journal for invoice={} tenant={}", invoice.getInvoiceNumber(), tenantId);
        } catch (Exception e) {
            log.error("Failed to post payment journal for invoice={} tenant={}: {}",
                    invoice.getId(), tenantId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<BookAgencyInvoiceResponse> getInvoices(TenantId tenantId, UUID clientId, Pageable pageable) {
        findActiveClient(tenantId, clientId);
        return invoiceRepo.findByClient(clientId, pageable).map(this::toInvoiceResponse);
    }

    // ── GL helper ─────────────────────────────────────────────────────────────

    private UUID findAccountByCode(TenantId tenantId, String code) {
        return accountingFacade.getAccounts(tenantId).stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookAgencyResource findResource(TenantId tenantId, UUID id) {
        return resourceRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", id.toString()));
    }

    private BookAgencyOffering findOffering(TenantId tenantId, UUID id) {
        return offeringRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Offering", id.toString()));
    }

    private BookAgencyBooking findBooking(TenantId tenantId, UUID id) {
        return bookingRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id.toString()));
    }

    private BookAgencyClient findActiveClient(TenantId tenantId, UUID id) {
        return clientRepo.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("BookAgencyClient", id.toString()));
    }

    private BookAgencyProfileResponse toProfileResponse(BookAgencyProfile p) {
        return new BookAgencyProfileResponse(p.getId(), p.getAgencyName(), p.getRegistrationNumber(),
                p.getEmail(), p.getPhone(), p.getPhysicalAddress(), p.getLogoUrl());
    }

    private BookAgencyClientResponse toClientResponse(BookAgencyClient c) {
        return new BookAgencyClientResponse(c.getId(), c.getTradingName(), c.getBusinessType(),
                c.getTimezone(), c.getContactName(), c.getContactEmail(), c.getContactPhone(),
                c.getOnboardedAt(), c.getStatus(), c.getNotes(), c.getCreatedAt());
    }

    private PortalAccessGrantResponse toGrantResponse(BookPortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(),
                g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt());
    }

    private ResourceResponse toResourceResponse(BookAgencyResource r) {
        return new ResourceResponse(r.getId(), r.getClientId(), r.getName(), r.getRoleDescription(),
                r.getWorkingHoursStart(), r.getWorkingHoursEnd(), r.isActive());
    }

    private OfferingResponse toOfferingResponse(BookAgencyOffering o) {
        return new OfferingResponse(o.getId(), o.getClientId(), o.getName(), o.getDurationMinutes(),
                o.getBufferMinutes(), o.getPrice(), o.isActive());
    }

    private BookingResponse toBookingResponse(BookAgencyBooking b, String resourceName, String offeringName) {
        return new BookingResponse(b.getId(), b.getBookingNumber(), b.getClientId(),
                b.getResourceId(), resourceName, b.getOfferingId(), offeringName,
                b.getCustomerName(), b.getCustomerPhone(), b.getCustomerEmail(),
                b.getStartDatetime(), b.getEndDatetime(), b.getStatus(), b.getNotes(), b.getCreatedAt());
    }

    private BookAgencyInvoiceResponse toInvoiceResponse(BookAgencyInvoice inv) {
        return new BookAgencyInvoiceResponse(inv.getId(), inv.getInvoiceNumber(), inv.getDescription(),
                inv.getPeriodStart(), inv.getPeriodEnd(), inv.getInvoiceDate(), inv.getDueDate(),
                inv.getSubtotal(), inv.getVatAmount(), inv.getTotal(), inv.getAmountPaid(), inv.balance(),
                inv.getStatus(), inv.getSentAt(), inv.getPaidAt());
    }
}