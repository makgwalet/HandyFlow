package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ProfitDtos.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceProfitabilityServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Mock ComplianceCostRateRepository rates;
    @Mock ComplianceTimeEntryRepository entries;
    @Mock ComplianceBillingEventRepository billing;
    @Mock ComplianceClientRepository clients;
    @Mock InvoicingFacade invoicing;
    @InjectMocks ComplianceProfitabilityService service;

    private ComplianceTimeEntry hours(int minutes, LocalDate day, String kind) {
        return ComplianceTimeEntry.record(TENANT, CLIENT, ME, day, minutes, "CS-REVIEW", kind, true, null, TO, true, 0);
    }

    @Test
    void unratedHoursAreCountedApartAndNotPriced() {
        when(rates.findAllForTenant(TENANT)).thenReturn(List.of());
        when(entries.findActiveBetween(TENANT, FROM, TO)).thenReturn(List.of(hours(120, FROM.plusDays(3), null)));
        when(billing.findInvoicedBetween(TENANT, FROM, TO)).thenReturn(List.of());
        when(clients.findAll(any(), any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        Report r = service.report(TENANT, FROM, TO);
        assertThat(r.unratedMinutes() == 120).isTrue();
        assertThat(r.labourCost().signum() == 0).isTrue();
        ClientLine c = r.clients().get(0);
        assertThat(c.costComplete()).isFalse();
        assertThat(c.marginPct() == null).isTrue();
    }

    @Test
    void costUsesTheRateInForceOnTheDayWorked() {
        ComplianceCostRate early = ComplianceCostRate.of(TENANT, ME, new BigDecimal("300"), LocalDate.of(2026, 1, 1), ME);
        ComplianceCostRate late = ComplianceCostRate.of(TENANT, ME, new BigDecimal("600"), LocalDate.of(2026, 11, 15), ME);
        when(rates.findAllForTenant(TENANT)).thenReturn(List.of(late, early));
        when(entries.findActiveBetween(TENANT, FROM, TO)).thenReturn(List.of(hours(60, FROM.plusDays(2), "REWORK"), hours(60, FROM.plusDays(20), null)));
        when(billing.findInvoicedBetween(TENANT, FROM, TO)).thenReturn(List.of());
        when(clients.findAll(any(), any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        Report r = service.report(TENANT, FROM, TO);
        assertThat(r.labourCost().compareTo(new BigDecimal("900.00")) == 0).isTrue();
        assertThat(r.unratedMinutes() == 0).isTrue();
        assertThat(r.clients().get(0).reworkMinutes() == 60).isTrue();
        assertThat(r.people().get(0).utilisationPct().compareTo(new BigDecimal("100.0")) == 0).isTrue();
        assertThat(r.services().get(0).serviceCode().equals("CS-REVIEW")).isTrue();
    }

    @Test
    void badPeriodRefused() {
        assertThatThrownBy(() -> service.report(TENANT, TO, FROM)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.report(TENANT, FROM.minusDays(400), TO)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void secondRateOnTheSameDayRefused() {
        when(rates.countOnDay(TENANT, ME, FROM)).thenReturn(1L);
        assertThatThrownBy(() -> service.setRate(TENANT, new CostRateRequest(ME, new BigDecimal("300"), FROM), ME)).isInstanceOf(HandyFlowException.class);
        verify(rates, never()).save(any());
    }

    @Test
    void settingARateSavesIt() {
        when(rates.countOnDay(TENANT, ME, FROM)).thenReturn(0L);
        when(rates.save(any(ComplianceCostRate.class))).thenAnswer(i -> i.getArgument(0));
        CostRateView v = service.setRate(TENANT, new CostRateRequest(ME, new BigDecimal("300"), FROM), ME);
        assertThat(v.hourlyCost().compareTo(new BigDecimal("300.00")) == 0).isTrue();
    }
}
