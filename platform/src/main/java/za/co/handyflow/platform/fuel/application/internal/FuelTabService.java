package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookings.application.BookingsFacade;
import za.co.handyflow.platform.bookings.application.BookingsFacade.ServiceInfo;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopOffering;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopOfferingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.TabDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Driver tabs at the truck stop. Prices are never typed here: a line takes the truck stop service's current Bookings price when it is added, and keeps
 * that price. Fuel is not sold on a tab, because forecourt shifts already count it. A tab is settled at the till or charged to a customer account
 * (a draft invoice, see {@link FuelTabInvoicingService}); a settled tab is final and an open one can be voided.
 */
@Service
@RequiredArgsConstructor
public class FuelTabService {

    private final FuelTruckStopTabRepository tabs;
    private final FuelTruckStopTabLineRepository lines;
    private final FuelTruckStopOfferingRepository offerings;
    private final FuelCustomerAccountRepository accounts;
    private final BookingsFacade bookings;

    @Transactional(readOnly = true)
    public List<TabView> list(TenantId tenant, String status, int limit) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null && !Set.of("OPEN", "SETTLED", "VOID").contains(s)) throw new IllegalArgumentException("Status must be OPEN, SETTLED or VOID.");
        List<FuelTruckStopTab> found = tabs.findRecent(tenant, s, PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
        Map<UUID, List<FuelTruckStopTabLine>> byTab = found.isEmpty() ? Map.of()
                : lines.findForTabs(tenant, found.stream().map(FuelTruckStopTab::getId).toList()).stream().collect(Collectors.groupingBy(FuelTruckStopTabLine::getTabId));
        return found.stream().map(t -> view(t, byTab.getOrDefault(t.getId(), List.of()))).toList();
    }

    @Transactional
    public TabView open(TenantId tenant, OpenRequest req, UUID by) {
        if (req.accountId() != null) requireChargeable(tenant, req.accountId());
        FuelTruckStopTab t = tabs.save(FuelTruckStopTab.open(tenant, req.vehicleRegistration(), req.driverName(), req.accountId(), by));
        return view(t, List.of());
    }

    /** Sets or clears the account while the tab is open. */
    @Transactional
    public TabView setAccount(TenantId tenant, UUID id, UUID accountId) {
        FuelTruckStopTab t = find(tenant, id);
        if (accountId != null) requireChargeable(tenant, accountId);
        t.setAccount(accountId);
        return view(tabs.save(t), lines.findForTab(tenant, id));
    }

    @Transactional
    public TabView addLine(TenantId tenant, UUID id, AddLineRequest req, UUID by) {
        FuelTruckStopTab t = find(tenant, id);
        t.requireOpen();
        FuelTruckStopOffering o = offerings.findOwned(tenant, req.offeringId()).orElseThrow(() -> new ResourceNotFoundException("Truck stop service", req.offeringId().toString()));
        if (!o.isActive()) throw new IllegalStateException("This service is switched off.");
        ServiceInfo svc = bookings.services(tenant).stream().filter(s -> s.id().equals(o.getBookingsServiceId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("This service no longer exists in Bookings."));
        if (!svc.active()) throw new IllegalStateException("This service is no longer active in Bookings.");
        lines.save(FuelTruckStopTabLine.of(tenant, id, o.getId(), svc.name(), req.quantity(), svc.price(), by));
        return view(t, lines.findForTab(tenant, id));
    }

    @Transactional
    public TabView removeLine(TenantId tenant, UUID id, UUID lineId) {
        FuelTruckStopTab t = find(tenant, id);
        t.requireOpen();
        FuelTruckStopTabLine l = lines.findOwned(tenant, id, lineId).orElseThrow(() -> new ResourceNotFoundException("Tab line", lineId.toString()));
        lines.delete(l);
        return view(t, lines.findForTab(tenant, id).stream().filter(x -> !x.getId().equals(lineId)).toList());
    }

    /** Settles a tab with no account at the till. The tab needs at least one line. */
    @Transactional
    public TabView settle(TenantId tenant, UUID id, String method, UUID by) {
        FuelTruckStopTab t = find(tenant, id);
        requireLines(tenant, id);
        t.settleAtTill(method, by);
        return view(tabs.save(t), lines.findForTab(tenant, id));
    }

    /** Charges a tab to its account and queues the draft invoice; the invoice itself is made by {@link FuelTabInvoicingService}. The account must still be chargeable. */
    @Transactional
    public TabView chargeToAccount(TenantId tenant, UUID id, UUID by) {
        FuelTruckStopTab t = find(tenant, id);
        requireLines(tenant, id);
        if (t.getAccountId() != null) requireChargeable(tenant, t.getAccountId());
        t.chargeToAccount(by);
        tabs.saveAndFlush(t);
        tabs.markPending(tenant, id);
        return view(find(tenant, id), lines.findForTab(tenant, id));
    }

    @Transactional
    public TabView voidTab(TenantId tenant, UUID id, String reason, UUID by) {
        FuelTruckStopTab t = find(tenant, id);
        t.voidTab(reason, by);
        return view(tabs.save(t), lines.findForTab(tenant, id));
    }

    @Transactional(readOnly = true)
    public long openCount(TenantId tenant) { return tabs.countOpen(tenant); }

    private void requireLines(TenantId tenant, UUID id) {
        if (lines.findForTab(tenant, id).isEmpty()) throw new IllegalStateException("Add at least one service to the tab first.");
    }

    private FuelCustomerAccount requireChargeable(TenantId tenant, UUID accountId) {
        FuelCustomerAccount a = accounts.findActiveById(tenant, accountId).orElseThrow(() -> new ResourceNotFoundException("Customer account", accountId.toString()));
        if (a.isCreditHold()) throw new IllegalStateException("This customer is on credit hold" + (a.getCreditHoldReason() != null ? " (" + a.getCreditHoldReason() + ")" : "")
                + ". Release the hold, or take payment at the till.");
        return a;
    }

    private FuelTruckStopTab find(TenantId tenant, UUID id) {
        return tabs.findOwned(tenant, id).orElseThrow(() -> new ResourceNotFoundException("Tab", id.toString()));
    }

    static TabView view(FuelTruckStopTab t, List<FuelTruckStopTabLine> ls) {
        List<LineView> lv = ls.stream().map(l -> new LineView(l.getId(), l.getOfferingId(), l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.amount())).toList();
        BigDecimal total = lv.stream().map(LineView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TabView(t.getId(), t.getVehicleRegistration(), t.getDriverName(), t.getAccountId(), t.getStatus(), t.getPaymentMethod(), t.getOpenedAt(), t.getSettledAt(),
                t.getVoidReason(), t.getInvoiceState(), t.getInvoiceNumber(), t.getInvoiceError(), lv, total);
    }
}
