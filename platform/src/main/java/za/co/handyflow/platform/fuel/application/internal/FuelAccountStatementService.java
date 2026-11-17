package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.InvoiceStanding;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.Line;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.Statement;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A statement of what Fuel charged to one customer account in a period: delivered fuel (dated by the day it was delivered), checked-out overnight stays
 * (the day the truck left) and charged driver tabs (the day they were charged), all in South African time. Read only. The division filter narrows it to
 * Distribution or Truck Stop. The invoices Fuel raised for these lines are shown with their standing in Invoicing (paid, credited, owed); Fuel keeps no
 * payment ledger of its own, and nothing from other modules is mixed in.
 */
@Service
@RequiredArgsConstructor
public class FuelAccountStatementService {

    static final int MAX_DAYS = 366;
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelCustomerAccountRepository accounts;
    private final FuelTruckStopStayRepository stays;
    private final FuelTruckStopTabRepository tabs;
    private final FuelTruckStopTabLineRepository tabLines;
    private final FuelDeliveryRepository deliveries;
    private final InvoicingFacade invoicing;

    @Transactional(readOnly = true)
    public Statement statement(TenantId tenant, UUID accountId, LocalDate from, LocalDate to) {
        return statement(tenant, accountId, from, to, null);
    }

    @Transactional(readOnly = true)
    public Statement statement(TenantId tenant, UUID accountId, LocalDate from, LocalDate to, String division) {
        if (from == null || to == null) throw new IllegalArgumentException("Give the first and last day of the statement.");
        if (to.isBefore(from)) throw new IllegalArgumentException("The last day cannot be before the first day.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) throw new IllegalArgumentException("A statement can cover at most " + MAX_DAYS + " days.");
        String div = division == null || division.isBlank() ? "ALL" : division.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "DISTRIBUTION", "TRUCK_STOP").contains(div)) throw new IllegalArgumentException("A statement covers Distribution (fuel deliveries) or Truck Stop, or both.");
        FuelCustomerAccount account = accounts.findActiveById(tenant, accountId).orElseThrow(() -> new ResourceNotFoundException("Customer account", accountId.toString()));

        List<Line> out = new ArrayList<>();
        if (!"TRUCK_STOP".equals(div)) {
            Instant start = from.atStartOfDay(SAST).toInstant(), end = to.plusDays(1).atStartOfDay(SAST).toInstant().minusMillis(1);
            for (FuelDelivery d : deliveries.findDeliveredBetween(tenant, start, end)) {
                if (!account.getCrmCustomerId().equals(d.getCustomerId()) || d.getTotalAmount() == null) continue;
                String desc = (d.getLitresDelivered() == null ? "" : d.getLitresDelivered().stripTrailingZeros().toPlainString() + " L ") + d.getFuelType() + " delivered"
                        + (d.getPricePerLitre() == null ? "" : " at " + d.getPricePerLitre().toPlainString() + " a litre");
                out.add(new Line(d.getDeliveredAt().atZone(SAST).toLocalDate(), "DELIVERY", d.getId(), d.getVehicleReg() == null ? "" : d.getVehicleReg(), desc,
                        d.getTotalAmount(), d.getInvoiceState(), d.getInvoiceNumber(), d.getInvoiceId()));
            }
        }
        if (!"DISTRIBUTION".equals(div)) {
            for (FuelTruckStopStay s : stays.findChargedToAccount(tenant, accountId, from, to)) {
                String desc = "Overnight parking, " + s.nights() + (s.nights() == 1 ? " night" : " nights") + " at " + s.getRatePerNight().toPlainString();
                out.add(new Line(s.getActualDepartureDate(), "STAY", s.getId(), s.getVehicleRegistration(), desc, s.amount(), s.getInvoiceState(), s.getInvoiceNumber(), s.getInvoiceId()));
            }
            List<FuelTruckStopTab> charged = tabs.findChargedToAccount(tenant, accountId, from.atStartOfDay(SAST).toInstant(), to.plusDays(1).atStartOfDay(SAST).toInstant());
            Map<UUID, List<FuelTruckStopTabLine>> byTab = charged.isEmpty() ? Map.of()
                    : tabLines.findForTabs(tenant, charged.stream().map(FuelTruckStopTab::getId).toList()).stream().collect(Collectors.groupingBy(FuelTruckStopTabLine::getTabId));
            for (FuelTruckStopTab t : charged) {
                List<FuelTruckStopTabLine> ls = byTab.getOrDefault(t.getId(), List.of());
                BigDecimal amount = ls.stream().map(FuelTruckStopTabLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                String desc = ls.stream().map(l -> l.getQuantity().stripTrailingZeros().toPlainString() + " x " + l.getDescription()).collect(Collectors.joining(", "));
                out.add(new Line(t.getSettledAt().atZone(SAST).toLocalDate(), "TAB", t.getId(), t.getVehicleRegistration(), desc, amount, t.getInvoiceState(), t.getInvoiceNumber(), t.getInvoiceId()));
            }
        }
        out.sort(Comparator.comparing(Line::date).thenComparing(Line::kind).thenComparing(Line::vehicleRegistration));

        BigDecimal total = BigDecimal.ZERO, invoiced = BigDecimal.ZERO;
        for (Line l : out) {
            total = total.add(l.amount());
            if ("INVOICED".equals(l.invoiceState())) invoiced = invoiced.add(l.amount());
        }

        List<InvoiceStanding> standing = new ArrayList<>();
        boolean known = true;
        Set<UUID> invoiceIds = out.stream().map(Line::invoiceId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!invoiceIds.isEmpty()) {
            try {
                for (InvoicingFacade.InvoiceSummary i : invoicing.findInvoiceSummaries(tenant, invoiceIds)) {
                    BigDecimal paid = i.amountPaid() == null ? BigDecimal.ZERO : i.amountPaid(), credited = i.creditedAmount() == null ? BigDecimal.ZERO : i.creditedAmount();
                    BigDecimal owed = i.total() == null ? BigDecimal.ZERO : i.total().subtract(paid).subtract(credited).max(BigDecimal.ZERO);
                    standing.add(new InvoiceStanding(i.id(), i.invoiceNumber(), i.status(), i.issuedAt(), i.total(), paid, credited, owed));
                }
                standing.sort(Comparator.comparing(InvoiceStanding::invoiceNumber, Comparator.nullsLast(Comparator.naturalOrder())));
            } catch (RuntimeException ex) {
                known = false;
                standing.clear();
            }
        }
        BigDecimal withVat = BigDecimal.ZERO, paid = BigDecimal.ZERO, credited = BigDecimal.ZERO, owed = BigDecimal.ZERO;
        for (InvoiceStanding i : standing) {
            withVat = withVat.add(i.total() == null ? BigDecimal.ZERO : i.total()); paid = paid.add(i.paid()); credited = credited.add(i.credited()); owed = owed.add(i.outstanding());
        }
        return new Statement(accountId, from, to, out, total, invoiced, total.subtract(invoiced), div, standing, withVat, paid, credited, owed, known);
    }
}
