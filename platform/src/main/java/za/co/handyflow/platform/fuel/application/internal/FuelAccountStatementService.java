package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.Line;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.Statement;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A statement of what the truck stop charged to one customer account in a period: checked-out overnight stays (dated by the day the truck left) and
 * charged driver tabs (dated by the day they were charged, South African time). Read only. It lists what was charged and how far each charge got in
 * Invoicing; it is not a ledger and does not know about payments, which belong to Invoicing.
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

    @Transactional(readOnly = true)
    public Statement statement(TenantId tenant, UUID accountId, LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Give the first and last day of the statement.");
        if (to.isBefore(from)) throw new IllegalArgumentException("The last day cannot be before the first day.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) throw new IllegalArgumentException("A statement can cover at most " + MAX_DAYS + " days.");
        accounts.findActiveById(tenant, accountId).orElseThrow(() -> new ResourceNotFoundException("Customer account", accountId.toString()));

        List<Line> out = new ArrayList<>();
        for (FuelTruckStopStay s : stays.findChargedToAccount(tenant, accountId, from, to)) {
            String desc = "Overnight parking, " + s.nights() + (s.nights() == 1 ? " night" : " nights") + " at " + s.getRatePerNight().toPlainString();
            out.add(new Line(s.getActualDepartureDate(), "STAY", s.getId(), s.getVehicleRegistration(), desc, s.amount(), s.getInvoiceState(), s.getInvoiceNumber()));
        }
        List<FuelTruckStopTab> charged = tabs.findChargedToAccount(tenant, accountId, from.atStartOfDay(SAST).toInstant(), to.plusDays(1).atStartOfDay(SAST).toInstant());
        Map<UUID, List<FuelTruckStopTabLine>> byTab = charged.isEmpty() ? Map.of()
                : tabLines.findForTabs(tenant, charged.stream().map(FuelTruckStopTab::getId).toList()).stream().collect(Collectors.groupingBy(FuelTruckStopTabLine::getTabId));
        for (FuelTruckStopTab t : charged) {
            List<FuelTruckStopTabLine> ls = byTab.getOrDefault(t.getId(), List.of());
            BigDecimal amount = ls.stream().map(FuelTruckStopTabLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String desc = ls.stream().map(l -> l.getQuantity().stripTrailingZeros().toPlainString() + " x " + l.getDescription()).collect(Collectors.joining(", "));
            out.add(new Line(t.getSettledAt().atZone(SAST).toLocalDate(), "TAB", t.getId(), t.getVehicleRegistration(), desc, amount, t.getInvoiceState(), t.getInvoiceNumber()));
        }
        out.sort(Comparator.comparing(Line::date).thenComparing(Line::kind).thenComparing(Line::vehicleRegistration));

        BigDecimal total = BigDecimal.ZERO, invoiced = BigDecimal.ZERO;
        for (Line l : out) {
            total = total.add(l.amount());
            if ("INVOICED".equals(l.invoiceState())) invoiced = invoiced.add(l.amount());
        }
        return new Statement(accountId, from, to, out, total, invoiced, total.subtract(invoiced));
    }
}
