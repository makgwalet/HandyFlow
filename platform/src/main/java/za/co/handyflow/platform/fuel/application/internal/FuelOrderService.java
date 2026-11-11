package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Stage 2A: quotations and sales orders with a credit check and stock allocation. */
@Service
@RequiredArgsConstructor
public class FuelOrderService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelSalesOrderRepository orders;
    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerAccountRepository accounts;
    private final FuelCustomerSiteRepository sites;
    private final FuelTankRepository tanks;
    private final FuelReservationRepository reservations;
    private final FuelStockService stock;
    private final FuelService fuelService;
    private final OrderNumberGenerator numbers;
    private final InvoicingFacade invoicing;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(TenantId tenantId, Pageable pageable) {
        return orders.findAllActive(tenantId, pageable).map(o -> toResponse(tenantId, o, false));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(TenantId tenantId, UUID id) {
        return toResponse(tenantId, find(tenantId, id), true);
    }

    @Transactional
    public OrderResponse create(TenantId tenantId, OrderRequest req, UUID by) {
        FuelCustomerAccount account = accounts.findActiveById(tenantId, req.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account", req.accountId().toString()));
        requireSiteOf(tenantId, req.siteId(), account);
        FuelSalesOrder o = FuelSalesOrder.create(tenantId, numbers.generate(tenantId), account.getId(), account.getCrmCustomerId(),
                req.siteId(), req.fuelType(), req.litres(), req.pricePerLitre(), req.requestedDate(), req.quoteValidUntil(),
                req.customerReference(), req.notes(), by);
        return toResponse(tenantId, orders.save(o), false);
    }

    @Transactional
    public OrderResponse revise(TenantId tenantId, UUID id, OrderRequest req) {
        FuelSalesOrder o = orders.findActiveByIdForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        FuelCustomerAccount account = accounts.findActiveById(tenantId, o.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account", o.getAccountId().toString()));
        requireSiteOf(tenantId, req.siteId(), account);
        o.revise(req.siteId(), req.fuelType(), req.litres(), req.pricePerLitre(), req.requestedDate(), req.quoteValidUntil(),
                req.customerReference(), req.notes());
        return toResponse(tenantId, orders.save(o), false);
    }

    /** What the customer's exposure would be if this order were confirmed. */
    @Transactional(readOnly = true)
    public CreditCheckResponse creditCheck(TenantId tenantId, UUID id) {
        FuelSalesOrder o = find(tenantId, id);
        FuelCustomerAccount account = accounts.findActiveById(tenantId, o.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account", o.getAccountId().toString()));
        return check(tenantId, o, account);
    }

    @Transactional
    public OrderResponse confirm(TenantId tenantId, UUID id, ConfirmOrderRequest req, boolean canOverrideCredit, UUID by) {
        FuelSalesOrder o = orders.findActiveByIdForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        if (!FuelSalesOrder.QUOTE.equals(o.getStatus())) {
            throw new IllegalStateException("Only a quotation can be confirmed (this order is " + o.getStatus().toLowerCase().replace('_', ' ') + ").");
        }
        FuelCustomerAccount account = accounts.findActiveById(tenantId, o.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account", o.getAccountId().toString()));
        if (account.isCreditHold()) {
            throw new IllegalStateException("This customer is on credit hold"
                    + (account.getCreditHoldReason() != null ? " (" + account.getCreditHoldReason() + ")" : "")
                    + ". Release the hold before confirming the order.");
        }
        FuelTank tank = tanks.findActiveById(tenantId, req.tankId())
                .orElseThrow(() -> new ResourceNotFoundException("Tank", req.tankId().toString()));
        if (!tank.getFuelType().equals(o.getFuelType())) {
            throw new IllegalArgumentException("That tank holds " + tank.getFuelType() + ", the order is for " + o.getFuelType() + ".");
        }
        CreditCheckResponse credit = check(tenantId, o, account);
        String override = req.overrideReason() == null ? "" : req.overrideReason().trim();
        if (!credit.withinLimit()) {
            BigDecimal over = credit.exposureAfter().subtract(credit.creditLimit());
            if (!canOverrideCredit) {
                throw new IllegalStateException("This order takes the customer R" + over.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " over their credit limit. A user with the credit override permission must confirm it.");
            }
            if (override.length() < 5) {
                throw new IllegalArgumentException("This order is over the credit limit: give a reason of at least 5 characters to override it.");
            }
        }
        o.confirm(tank.getId(), credit.withinLimit() ? null : override, LocalDate.now(SAST));
        orders.save(o);
        // Allocation: stock is promised to this order; refuses if the tank cannot cover it.
        stock.reserve(tenantId, new ReservationRequest(tank.getId(), o.getLitresOrdered(), o.getCrmCustomerId(), "ORDER", o.getId(),
                null, "Order " + o.getOrderNumber()), by);
        return toResponse(tenantId, o, true);
    }

    @Transactional
    public OrderResponse cancel(TenantId tenantId, UUID id, OrderCancelRequest req) {
        FuelSalesOrder o = orders.findActiveByIdForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        if (deliveries.sumPendingForOrder(tenantId, id).signum() > 0) {
            throw new IllegalStateException("This order still has scheduled loads. Cancel those deliveries first.");
        }
        o.cancel(req.reason());
        orders.save(o);
        if (o.getTankId() != null) {
            for (FuelReservation r : reservations.findActiveByReference(tenantId, o.getTankId(), "ORDER", id)) {
                r.release();
                reservations.save(r);
            }
        }
        return toResponse(tenantId, o, true);
    }

    /** Schedules one load against the order. The order's own reservation covers it; the total can never exceed the order. */
    @Transactional
    public DeliveryResponse scheduleLoad(TenantId tenantId, UUID id, OrderDeliveryRequest req) {
        FuelSalesOrder o = orders.findActiveByIdForUpdate(tenantId, id).orElseThrow(() -> notFound(id));
        if (!o.isOpen()) {
            throw new IllegalStateException("Loads can only be scheduled for a confirmed order (this one is "
                    + o.getStatus().toLowerCase().replace('_', ' ') + ").");
        }
        BigDecimal delivered = deliveries.sumDeliveredForOrder(tenantId, id);
        BigDecimal pending = deliveries.sumPendingForOrder(tenantId, id);
        BigDecimal left = o.getLitresOrdered().subtract(delivered).subtract(pending);
        if (req.litres().compareTo(left) > 0) {
            throw new IllegalArgumentException("Only " + left.max(BigDecimal.ZERO).toPlainString() + " L of this order is not yet delivered or scheduled.");
        }
        Map<String, String> address = req.deliveryAddress();
        if ((address == null || address.isEmpty()) && o.getSiteId() != null) {
            FuelCustomerSite s = sites.findActiveById(tenantId, o.getSiteId()).orElse(null);
            if (s != null && s.getAddress() != null) address = Map.of("street", s.getAddress());
        }
        DeliveryResponse d = fuelService.scheduleDelivery(tenantId, new CreateDeliveryRequest(o.getTankId(), o.getCrmCustomerId(),
                address == null ? Map.of() : address, o.getFuelType(), req.litres(), o.getPricePerLitre(), req.scheduledAt(),
                req.driverName(), req.vehicleReg(), o.getSiteId()));
        fuelService.attachToOrder(tenantId, d.id(), id);
        return fuelService.deliveriesForOrder(tenantId, id).stream().filter(x -> x.id().equals(d.id())).findFirst().orElse(d);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CreditCheckResponse check(TenantId tenantId, FuelSalesOrder o, FuelCustomerAccount account) {
        UUID customerId = o.getCrmCustomerId();
        BigDecimal ar = BigDecimal.ZERO;
        for (InvoicingFacade.OutstandingInvoiceSummary s : invoicing.findOutstandingInvoices(tenantId)) {
            if (!customerId.equals(s.customerId())) continue;
            BigDecimal owed = (s.total() != null ? s.total() : BigDecimal.ZERO).subtract(s.settled());
            if (owed.signum() > 0) ar = ar.add(owed);
        }
        BigDecimal uninvoiced = deliveries.sumUninvoicedValue(tenantId, customerId);
        BigDecimal others = BigDecimal.ZERO;
        for (FuelSalesOrder other : orders.findOpenForCustomer(tenantId, customerId)) {
            if (other.getId().equals(o.getId())) continue;
            BigDecimal remaining = other.getLitresOrdered().subtract(deliveries.sumDeliveredForOrder(tenantId, other.getId()));
            if (remaining.signum() > 0) others = others.add(remaining.multiply(other.getPricePerLitre()));
        }
        BigDecimal thisOrder = o.value();
        BigDecimal after = ar.add(uninvoiced).add(others).add(thisOrder);
        return new CreditCheckResponse(account.getCreditLimit(), ar, uninvoiced, others, thisOrder, after,
                after.compareTo(account.getCreditLimit()) <= 0, account.isCreditHold());
    }

    private void requireSiteOf(TenantId tenantId, UUID siteId, FuelCustomerAccount account) {
        if (siteId == null) return;
        FuelCustomerSite s = sites.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery site", siteId.toString()));
        if (!s.getAccountId().equals(account.getId())) throw new IllegalArgumentException("That delivery site belongs to a different customer.");
    }

    private FuelSalesOrder find(TenantId tenantId, UUID id) {
        return orders.findActiveById(tenantId, id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(UUID id) { return new ResourceNotFoundException("Order", id.toString()); }

    private OrderResponse toResponse(TenantId tenantId, FuelSalesOrder o, boolean withDeliveries) {
        String customer = crm.findCustomerById(tenantId, o.getCrmCustomerId()).map(CustomerSummary::name).orElse("(customer removed)");
        String siteName = o.getSiteId() == null ? null : sites.findActiveById(tenantId, o.getSiteId()).map(FuelCustomerSite::getName).orElse(null);
        String tankName = o.getTankId() == null ? null : tanks.findActiveById(tenantId, o.getTankId()).map(FuelTank::getName).orElse(null);
        BigDecimal delivered = deliveries.sumDeliveredForOrder(tenantId, o.getId());
        BigDecimal pending = deliveries.sumPendingForOrder(tenantId, o.getId());
        BigDecimal remaining = FuelSalesOrder.CANCELLED.equals(o.getStatus()) ? BigDecimal.ZERO
                : o.getLitresOrdered().subtract(delivered).max(BigDecimal.ZERO);
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getAccountId(), o.getCrmCustomerId(), customer,
                o.getSiteId(), siteName, o.getFuelType(), o.getLitresOrdered(), o.getPricePerLitre(), o.value(), o.getStatus(),
                o.getRequestedDate(), o.getQuoteValidUntil(), o.getTankId(), tankName, delivered, pending, remaining,
                o.getCreditOverrideReason(), o.getCancelReason(), o.getCustomerReference(), o.getNotes(), o.getCreatedAt(),
                withDeliveries ? fuelService.deliveriesForOrder(tenantId, o.getId()) : null);
    }
}
