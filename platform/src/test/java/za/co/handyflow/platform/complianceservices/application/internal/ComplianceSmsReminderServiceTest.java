package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDeadline;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceSmsConsent;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceSmsLog;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceSmsConsentRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceSmsLogRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceSmsSettingsRepository;
import za.co.handyflow.platform.complianceservices.dto.SmsReminderDtos.ConsentRequest;
import za.co.handyflow.platform.complianceservices.dto.SmsReminderDtos.WithdrawRequest;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceSmsReminderServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceSmsSettingsRepository settings;
    @Mock ComplianceSmsConsentRepository consents;
    @Mock ComplianceSmsLogRepository smsLog;
    @Mock ComplianceClientRepository clients;
    @Mock NotificationService notifications;
    @InjectMocks ComplianceSmsReminderService service;

    private ComplianceSmsConsent consent(UUID client) {
        return ComplianceSmsConsent.record(TENANT, client, "Thandi", "+27821234567", "Agreed by phone on 3 Nov", ME);
    }

    private ClientComplianceDeadline deadline(UUID client) {
        return ClientComplianceDeadline.create(TENANT, client, null, "ANNUAL_RETURN", "Annual return", TODAY.plusDays(1), ME);
    }

    @Test
    void consentForAnUnknownClientIsRefused() {
        UUID client = UUID.randomUUID();
        when(clients.findByIdForTenant(TENANT, client)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordConsent(TENANT, client, new ConsentRequest("Thandi", "+27821234567", "Agreed by phone on 3 Nov"), ME))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(consents, never()).save(any());
    }

    @Test
    void withdrawalWithoutAReasonIsRefused() {
        ComplianceSmsConsent c = consent(UUID.randomUUID());
        when(consents.findOne(TENANT, c.getId())).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.withdraw(TENANT, c.getId(), new WithdrawRequest("no"), ME)).isInstanceOf(HandyFlowException.class);
        verify(consents, never()).save(any());
    }

    @Test
    void aWithdrawnNumberIsNotTextedEvenIfItWasActiveWhenTheRunStarted() {
        UUID client = UUID.randomUUID();
        ComplianceSmsConsent c = consent(client);
        c.withdraw(ME, "Asked us to stop by email");
        when(smsLog.findExisting(eq(TENANT), any(), eq(c.getId()), eq("DAY_BEFORE"))).thenReturn(List.of());
        when(consents.findOne(TENANT, c.getId())).thenReturn(Optional.of(c));
        String r = service.sendOne(TENANT, deadline(client), c, "DAY_BEFORE", TODAY);
        org.junit.jupiter.api.Assertions.assertEquals("NO_CONSENT", r);
        verifyNoInteractions(notifications);
    }

    @Test
    void aTextAlreadyHandedOverIsNotSentAgain() {
        UUID client = UUID.randomUUID();
        ComplianceSmsConsent c = consent(client);
        ClientComplianceDeadline d = deadline(client);
        when(smsLog.findExisting(TENANT, d.getId(), c.getId(), "DAY_BEFORE"))
                .thenReturn(List.of(ComplianceSmsLog.of(TENANT, client, d.getId(), c.getId(), "DAY_BEFORE", TODAY, "HANDED_OVER", null)));
        org.junit.jupiter.api.Assertions.assertEquals("ALREADY_SENT", service.sendOne(TENANT, d, c, "DAY_BEFORE", TODAY));
        verifyNoInteractions(notifications);
    }

    @Test
    void aConsentingNumberIsTextedOnceAndRecordedAsHandedOver() {
        UUID client = UUID.randomUUID();
        ComplianceSmsConsent c = consent(client);
        ClientComplianceDeadline d = deadline(client);
        when(smsLog.findExisting(TENANT, d.getId(), c.getId(), "DAY_BEFORE")).thenReturn(List.of());
        when(consents.findOne(TENANT, c.getId())).thenReturn(Optional.of(c));
        org.junit.jupiter.api.Assertions.assertEquals("HANDED_OVER", service.sendOne(TENANT, d, c, "DAY_BEFORE", TODAY));
        verify(notifications).send(any(NotificationRequest.class));
        verify(smsLog).save(any(ComplianceSmsLog.class));
    }
}
