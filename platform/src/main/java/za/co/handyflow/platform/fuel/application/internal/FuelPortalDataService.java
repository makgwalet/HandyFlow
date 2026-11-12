package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelPortalAccessGrant;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.OrderRequest;
import za.co.handyflow.platform.fuel.dto.OrderResponse;
import za.co.handyflow.platform.fuel.dto.PortalDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

/**
 * What a customer sees. The portal token proves who they are; only an ACTIVE grant proves what they may see.
 * Every method goes through {@link #requireGrant} first, so a customer can never reach another customer's account by guessing an id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelPortalDataService {

    /** A customer cannot pile up unreviewed requests. */
    static final int MAX_OPEN_REQUESTS = 10;
    static final String PORTAL_NOTE = "Requested through the customer portal";

    private final FuelPortalAccessGrantRepository grants;
    private final FuelCustomerAccountRepository accounts;
    private final FuelCustomerSiteRepository sites;
    private final FuelSalesOrderRepository orders;
    private final FuelDeliveryRepository deliveries;
    private final FuelOrderService orderService;
    private final CrmFacade crm;
    private final PortalUserRepository portalUsers;
    private final NotificationService notifications;
    private final TenantAdminRecipients adminRecipients;
    private final FeatureGuard featureGuard;

    @Transactional(readOnly = true)
    public List<AccountSummary> myAccounts(UUID portalUserId) {
        List<AccountSummary> out = new ArrayList<>();
        for (FuelPortalAccessGrant g : grants.findActiveGrantsForUser(portalUserId)) {
            TenantId tenant = TenantId.of(g.getTenantId());
            if (!featureGuard.hasModuleFor(tenant, "fuel")) continue; // the supplier's plan no longer includes Fuel
            accounts.findActiveById(tenant, g.getAccountId()).ifPresent(a -> out.add(
                    new AccountSummary(a.getId(), crm.findCustomerById(tenant, a.getCrmCustomerId()).map(CustomerSummary::name).orElse("Customer"), a.getPaymentTermsDays())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<SiteSummary> mySites(UUID portalUserId, UUID accountId) {
        FuelPortalAccessGrant g = requireGrant(portalUserId, accountId);
        return sites.findByAccount(TenantId.of(g.getTenantId()), accountId).stream().map(s -> new SiteSummary(s.getId(), s.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderView> myOrders(UUID portalUserId, UUID accountId) {
        FuelPortalAccessGrant g = requireGrant(portalUserId, accountId);
        TenantId tenant = TenantId.of(g.getTenantId());
        Map<UUID, String> siteNames = siteNames(tenant, accountId);
        return orders.findByAccount(tenant, accountId).stream().map(o -> toView(tenant, o, siteNames)).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> myDeliveries(UUID portalUserId, UUID accountId) {
        FuelPortalAccessGrant g = requireGrant(portalUserId, accountId);
        TenantId tenant = TenantId.of(g.getTenantId());
        FuelCustomerAccount account = accounts.findActiveById(tenant, accountId).orElseThrow(() -> notFound());
        Map<UUID, String> siteNames = siteNames(tenant, accountId);
        Map<UUID, String> orderNumbers = new HashMap<>();
        for (FuelSalesOrder o : orders.findByAccount(tenant, accountId)) orderNumbers.put(o.getId(), o.getOrderNumber());
        return deliveries.findDeliveredForCustomer(tenant, account.getCrmCustomerId()).stream()
                .map(d -> new DeliveryView(d.getId(), d.getDeliveredAt(), d.getOrderId() == null ? null : orderNumbers.get(d.getOrderId()),
                        d.getSiteId() == null ? null : siteNames.get(d.getSiteId()), d.getFuelType(), d.getLitresDelivered(),
                        d.getPricePerLitre(), d.getTotalAmount(), d.getReceiptNumber()))
                .toList();
    }

    /**
     * A request, not an order: it is saved as a quotation with no price typed, so the price list prices it, and staff
     * confirm it (the credit check runs then). A customer on credit hold can still ask; staff decide.
     */
    @Transactional
    public OrderView requestOrder(UUID portalUserId, UUID accountId, OrderRequestBody req) {
        FuelPortalAccessGrant g = requireGrant(portalUserId, accountId);
        TenantId tenant = TenantId.of(g.getTenantId());
        long open = orders.findByAccount(tenant, accountId).stream()
                .filter(o -> FuelSalesOrder.QUOTE.equals(o.getStatus()) && o.getNotes() != null && o.getNotes().startsWith(PORTAL_NOTE)).count();
        if (open >= MAX_OPEN_REQUESTS) {
            throw new HandyFlowException("You already have " + open + " requests waiting for us. Please wait for them to be confirmed, or call us.", HttpStatus.CONFLICT, "TOO_MANY_REQUESTS");
        }
        String email = portalUsers.findById(portalUserId).map(u -> u.getEmail()).orElse("a portal user");
        String note = PORTAL_NOTE + " by " + email + (req.notes() == null || req.notes().isBlank() ? "" : ": " + req.notes().trim());
        OrderResponse created;
        try {
            created = orderService.create(tenant, new OrderRequest(accountId, req.siteId(), req.fuelType(), req.litres(), null, req.requestedDate(),
                    null, req.customerReference(), note), null);
        } catch (IllegalArgumentException e) {
            // Staff-facing wording ("Add one under Pricing") must not reach a customer.
            if (e.getMessage() != null && e.getMessage().startsWith("No price is set")) {
                throw new HandyFlowException("We could not price this request automatically. Please call us to place it.", HttpStatus.UNPROCESSABLE_ENTITY, "NOT_PRICED");
            }
            throw e;
        }
        notifyStaff(tenant, created, email);
        return new OrderView(created.id(), created.orderNumber(), created.siteName(), created.fuelType(), created.litresOrdered(), created.pricePerLitre(),
                created.value(), created.status(), created.requestedDate(), created.customerReference(), created.litresDelivered(), created.litresRemaining(), created.createdAt());
    }

    private FuelPortalAccessGrant requireGrant(UUID portalUserId, UUID accountId) {
        FuelPortalAccessGrant g = grants.findActiveGrant(portalUserId, accountId).orElseThrow(FuelPortalDataService::notFound);
        // The portal request has no tenant context, so the module check uses the grant's tenant. Same answer as "not yours".
        if (!featureGuard.hasModuleFor(TenantId.of(g.getTenantId()), "fuel")) throw notFound();
        return g;
    }

    /** Same answer for "no such account" and "not yours", so ids cannot be probed. */
    private static HandyFlowException notFound() {
        return new HandyFlowException("Not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private Map<UUID, String> siteNames(TenantId tenant, UUID accountId) {
        Map<UUID, String> m = new HashMap<>();
        for (FuelCustomerSite s : sites.findByAccount(tenant, accountId)) m.put(s.getId(), s.getName());
        return m;
    }

    private OrderView toView(TenantId tenant, FuelSalesOrder o, Map<UUID, String> siteNames) {
        BigDecimal delivered = deliveries.sumDeliveredForOrder(tenant, o.getId());
        BigDecimal remaining = o.getLitresOrdered().subtract(delivered).max(BigDecimal.ZERO);
        return new OrderView(o.getId(), o.getOrderNumber(), o.getSiteId() == null ? null : siteNames.get(o.getSiteId()), o.getFuelType(),
                o.getLitresOrdered(), o.getPricePerLitre(), o.getLitresOrdered().multiply(o.getPricePerLitre()).setScale(2, java.math.RoundingMode.HALF_UP),
                o.getStatus(), o.getRequestedDate(), o.getCustomerReference(), delivered, remaining, o.getCreatedAt());
    }

    /** Tells the tenant's admins a customer has asked for fuel. Never fails the request: the order is already saved. */
    private void notifyStaff(TenantId tenant, OrderResponse created, String requestedBy) {
        try {
            List<Recipient> recipients = adminRecipients.resolveTenantAdmins(tenant);
            if (recipients.isEmpty()) return;
            notifications.send(NotificationRequest.builder()
                    .tenantId(tenant)
                    .type(NotificationType.FUEL_PORTAL_REQUEST)
                    .title("Fuel request from a customer")
                    .message(requestedBy + " asked for " + created.litresOrdered() + " L of " + created.fuelType()
                            + (created.siteName() == null ? "" : " at " + created.siteName()) + " (" + created.orderNumber()
                            + "). It is waiting to be confirmed.")
                    .actionUrl("/fuel/orders")
                    .sourceModule("fuel")
                    .sourceEntityId(String.valueOf(created.id()))
                    .recipients(recipients)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Could not notify staff of portal fuel request {}: {}", created.orderNumber(), ex.getMessage());
        }
    }
}
