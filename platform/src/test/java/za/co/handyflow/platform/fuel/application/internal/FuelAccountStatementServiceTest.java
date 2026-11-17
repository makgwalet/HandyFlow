package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.Statement;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelAccountStatementServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelTruckStopStayRepository stays;
    @Mock FuelTruckStopTabRepository tabs;
    @Mock FuelTruckStopTabLineRepository tabLines;
    @InjectMocks FuelAccountStatementService service;

    FuelCustomerAccount account;
    final LocalDate from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 30);

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, UUID.randomUUID(), "OTHER", bd("5000"), 30, null, null);
        when(accounts.findActiveById(T, account.getId())).thenReturn(Optional.of(account));
    }

    @Test @DisplayName("lists stays by the day the truck left and tabs by the day they were charged, oldest first, with the total")
    void listsBoth() {
        FuelTruckStopStay stay = FuelTruckStopStay.book(T, "GP 1", "Sipho", null, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6), bd("450"), null, null);
        stay.chargeTo(account.getId()); stay.checkIn(); stay.checkOut(LocalDate.of(2026, 11, 6));
        FuelTruckStopTab tab = FuelTruckStopTab.open(T, "CA 9", null, account.getId(), null);
        tab.chargeToAccount(null);
        when(stays.findChargedToAccount(T, account.getId(), from, to)).thenReturn(List.of(stay));
        when(tabs.findChargedToAccount(eq(T), eq(account.getId()), any(), any())).thenReturn(List.of(tab));
        when(tabLines.findForTabs(eq(T), any())).thenReturn(List.of(
                FuelTruckStopTabLine.of(T, tab.getId(), UUID.randomUUID(), "Shower", bd("2"), bd("45"), null),
                FuelTruckStopTabLine.of(T, tab.getId(), UUID.randomUUID(), "Meal", bd("1"), bd("80"), null)));
        Statement s = service.statement(T, account.getId(), from, to);
        assertEquals(2, s.lines().size());
        assertEquals("STAY", s.lines().get(0).kind());
        assertEquals(0, bd("1350.00").compareTo(s.lines().get(0).amount()));
        assertEquals("TAB", s.lines().get(1).kind());
        assertEquals(0, bd("170.00").compareTo(s.lines().get(1).amount()));
        assertEquals("2 x Shower, 1 x Meal", s.lines().get(1).description());
        assertEquals(0, bd("1520.00").compareTo(s.total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(s.invoiced()));
        assertEquals(0, bd("1520.00").compareTo(s.notInvoiced()));
    }

    @Test @DisplayName("the tab range is the South African days asked for, from the first midnight to the midnight after the last day")
    void tabRangeIsSastDays() {
        service.statement(T, account.getId(), from, to);
        verify(tabs).findChargedToAccount(T, account.getId(), from.atStartOfDay(SAST).toInstant(), to.plusDays(1).atStartOfDay(SAST).toInstant());
    }

    @Test @DisplayName("a period with nothing charged gives an empty statement with zero totals")
    void empty() {
        Statement s = service.statement(T, account.getId(), from, to);
        assertTrue(s.lines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.total()));
    }

    @Test @DisplayName("a bad period or an unknown account is refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.statement(T, account.getId(), to, from));
        assertThrows(IllegalArgumentException.class, () -> service.statement(T, account.getId(), null, to));
        assertThrows(IllegalArgumentException.class, () -> service.statement(T, account.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 30)));
        assertThrows(ResourceNotFoundException.class, () -> service.statement(T, UUID.randomUUID(), from, to));
        verifyNoInteractions(stays, tabs);
    }
}
