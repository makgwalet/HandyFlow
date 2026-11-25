package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.TimeDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceTimeServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceTimeEntryRepository entries;
    @Mock ComplianceClientRepository clients;
    @InjectMocks ComplianceTimeService service;

    private void clientExists() {
        ComplianceClient c = mock(ComplianceClient.class);
        when(c.getName()).thenReturn("Acme");
        when(clients.findByIdForTenant(TENANT, CLIENT)).thenReturn(Optional.of(c));
    }

    @Test
    void recordsTimeAgainstAClient() {
        clientExists();
        when(entries.minutesOnDay(TENANT, ME, TODAY)).thenReturn(60L);
        when(entries.save(any(ComplianceTimeEntry.class))).thenAnswer(i -> i.getArgument(0));
        EntryView v = service.record(TENANT, new RecordRequest(CLIENT, TODAY, 90, "CS-REVIEW", null, null, null), ME, false, TODAY);
        assertThat(v.minutes() == 90).isTrue();
        assertThat(v.billable()).isTrue();
        assertThat(v.clientName().equals("Acme")).isTrue();
    }

    @Test
    void theDayIsLockedBeforeItsMinutesAreCounted() {
        clientExists();
        when(entries.minutesOnDay(TENANT, ME, TODAY)).thenReturn(0L);
        when(entries.save(any(ComplianceTimeEntry.class))).thenAnswer(i -> i.getArgument(0));
        service.record(TENANT, new RecordRequest(CLIENT, TODAY, 30, null, null, null, null), ME, false, TODAY);
        org.mockito.InOrder order = inOrder(entries);
        order.verify(entries).lockDay(any(String.class));
        order.verify(entries).minutesOnDay(TENANT, ME, TODAY);
    }

    @Test
    void refusesMoreThanSixteenHoursInADay() {
        clientExists();
        when(entries.minutesOnDay(TENANT, ME, TODAY)).thenReturn(950L);
        assertThatThrownBy(() -> service.record(TENANT, new RecordRequest(CLIENT, TODAY, 30, null, null, true, null), ME, false, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(entries, never()).save(any());
    }

    @Test
    void onlyTheOwnerOrAnAdministratorMayVoid() {
        ComplianceTimeEntry e = ComplianceTimeEntry.record(TENANT, CLIENT, ME, TODAY, 30, null, null, true, null, TODAY, false, 0);
        when(entries.findOne(TENANT, e.getId())).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.voidEntry(TENANT, e.getId(), OTHER, false, "Entered against the wrong client")).isInstanceOf(HandyFlowException.class);
        assertThat(e.isActive()).isTrue();
        clientExists();
        when(entries.save(any(ComplianceTimeEntry.class))).thenAnswer(i -> i.getArgument(0));
        EntryView v = service.voidEntry(TENANT, e.getId(), OTHER, true, "Entered against the wrong client");
        assertThat(v.status().equals("VOID")).isTrue();
    }

    @Test
    void summaryIgnoresVoidedEntries() {
        ComplianceTimeEntry good = ComplianceTimeEntry.record(TENANT, CLIENT, ME, TODAY, 60, null, "REWORK", true, null, TODAY, false, 0);
        ComplianceTimeEntry bad = ComplianceTimeEntry.record(TENANT, CLIENT, ME, TODAY, 45, null, null, true, null, TODAY, false, 0);
        bad.voidIt(ME, "Entered against the wrong client");
        when(entries.findForUser(TENANT, ME, TODAY.minusDays(6), TODAY)).thenReturn(java.util.List.of(good, bad));
        when(clients.findAll(any(), any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        Summary s = service.summary(TENANT, ME, TODAY.minusDays(6), TODAY);
        assertThat(s.minutes() == 60).isTrue();
        assertThat(s.reworkMinutes() == 60).isTrue();
        assertThat(s.byClient().size() == 1).isTrue();
    }

    @Test
    void badRangeRefused() {
        assertThatThrownBy(() -> service.summary(TENANT, ME, TODAY, TODAY.minusDays(1))).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.mine(TENANT, ME, TODAY.minusDays(400), TODAY)).isInstanceOf(HandyFlowException.class);
    }
}
