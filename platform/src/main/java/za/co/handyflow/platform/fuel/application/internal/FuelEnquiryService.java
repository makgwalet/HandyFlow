package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelEnquiry;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelEnquiryRepository;
import za.co.handyflow.platform.fuel.dto.EnquiryDtos.*;
import za.co.handyflow.platform.fuel.dto.OrderRequest;
import za.co.handyflow.platform.fuel.dto.OrderResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * B06: customer enquiries. An enquiry is raised, then either turned into a quotation (which is an ordinary FuelSalesOrder in
 * QUOTE status, with the usual pricing, revision history and confirmation) or closed as lost or cancelled with a reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelEnquiryService {

    private final FuelEnquiryRepository enquiries;
    private final FuelCustomerAccountRepository accounts;
    private final FuelOrderService orders;
    private final EnquiryNumberGenerator numbers;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public List<EnquiryResponse> list(TenantId tenantId, String status, int limit) {
        var page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<FuelEnquiry> rows = status == null || status.isBlank() ? enquiries.findRecent(tenantId, page) : enquiries.findByStatus(tenantId, status.trim().toUpperCase(), page);
        return rows.stream().map(e -> toResponse(tenantId, e)).toList();
    }

    @Transactional(readOnly = true)
    public long openCount(TenantId tenantId) { return enquiries.countNew(tenantId); }

    @Transactional
    public EnquiryResponse create(TenantId tenantId, EnquiryRequest req, UUID by) {
        requireAccount(tenantId, req.accountId());
        FuelEnquiry e = FuelEnquiry.create(tenantId, numbers.generate(tenantId), req.accountId(), req.contactName(), req.contactPhone(), req.contactEmail(),
                req.fuelType(), req.litres(), req.requestedDate(), req.source(), req.notes(), by);
        enquiries.save(e);
        log.info("Fuel enquiry {} created by={}", e.getEnquiryNumber(), by);
        return toResponse(tenantId, e);
    }

    @Transactional
    public EnquiryResponse update(TenantId tenantId, UUID id, EnquiryRequest req) {
        FuelEnquiry e = enquiries.findOwnedForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        requireAccount(tenantId, req.accountId());
        e.revise(req.accountId(), req.contactName(), req.contactPhone(), req.contactEmail(), req.fuelType(), req.litres(), req.requestedDate(), req.source(), req.notes());
        return toResponse(tenantId, enquiries.save(e));
    }

    /** Raises the quotation from the enquiry's fuel, litres and date. The enquiry needs a customer account first; a quote cannot exist without one. */
    @Transactional
    public EnquiryResponse quote(TenantId tenantId, UUID id, QuoteRequest req, UUID by) {
        FuelEnquiry e = enquiries.findOwnedForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        if (!e.isNew()) throw new IllegalStateException("This enquiry is already " + e.getStatus().toLowerCase() + ".");
        if (e.getAccountId() == null) throw new IllegalStateException("Link this enquiry to a customer account before quoting.");
        OrderResponse quote = orders.create(tenantId, new OrderRequest(e.getAccountId(), req == null ? null : req.siteId(), e.getFuelType(), e.getLitres(),
                req == null ? null : req.pricePerLitre(), e.getRequestedDate(), req == null ? null : req.quoteValidUntil(),
                req != null && req.customerReference() != null ? req.customerReference() : e.getEnquiryNumber(),
                "From enquiry " + e.getEnquiryNumber()), by);
        e.quoted(quote.id());
        enquiries.save(e);
        log.info("Fuel enquiry {} quoted as {} by={}", e.getEnquiryNumber(), quote.orderNumber(), by);
        return toResponse(tenantId, e, quote.orderNumber());
    }

    @Transactional
    public EnquiryResponse lost(TenantId tenantId, UUID id, String reason) {
        FuelEnquiry e = enquiries.findOwnedForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        e.lost(reason);
        return toResponse(tenantId, enquiries.save(e));
    }

    @Transactional
    public EnquiryResponse cancel(TenantId tenantId, UUID id, String reason) {
        FuelEnquiry e = enquiries.findOwnedForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        e.cancel(reason);
        return toResponse(tenantId, enquiries.save(e));
    }

    // ---- helpers ----

    private void requireAccount(TenantId tenantId, UUID accountId) {
        if (accountId != null && accounts.findActiveById(tenantId, accountId).isEmpty()) throw new ResourceNotFoundException("Customer account", accountId.toString());
    }

    private static ResourceNotFoundException notFound(UUID id) { return new ResourceNotFoundException("Enquiry", String.valueOf(id)); }

    private EnquiryResponse toResponse(TenantId tenantId, FuelEnquiry e) { return toResponse(tenantId, e, null); }

    private EnquiryResponse toResponse(TenantId tenantId, FuelEnquiry e, String quoteNumber) {
        String accountName = null;
        if (e.getAccountId() != null) {
            accountName = accounts.findActiveById(tenantId, e.getAccountId()).map(FuelCustomerAccount::getCrmCustomerId)
                    .flatMap(c -> crm.findCustomerById(tenantId, c)).map(CustomerSummary::name).orElse("(customer removed)");
        }
        return new EnquiryResponse(e.getId(), e.getEnquiryNumber(), e.getAccountId(), accountName, e.getContactName(), e.getContactPhone(), e.getContactEmail(),
                e.getFuelType(), e.getLitres(), e.getRequestedDate(), e.getSource(), e.getStatus(), e.getQuoteOrderId(), quoteNumber,
                e.getDecisionReason(), e.getNotes(), e.getCreatedAt(), e.getDecidedAt());
    }
}
