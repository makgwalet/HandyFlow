package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ReminderDtos.*;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceReminderServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceReminderSettingsRepository settings;
    @Mock ComplianceReminderRecipientRepository recipients;
    @Mock ComplianceReminderLogRepository log;
    @Mock ComplianceWorkQueueService queue;
    @Mock UserRecipientResolver resolver;
    @Mock NotificationService notifications;

    private ComplianceReminderService service;
    private ComplianceReminderSettings on;
    private final List<ComplianceReminderLog> logged = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ComplianceReminderService(settings, recipients, log, queue, resolver, notifications);
        on = ComplianceReminderSettings.blank(TENANT);
        on.apply(true, "HIGH", 25, 1, ADMIN);
        lenient().when(resolver.resolveUser(eq(TENANT), any())).thenAnswer(i -> Optional.of(Recipient.user(i.getArgument(1), "Thandi", "thandi@example.org", null)));
        lenient().when(log.findForDay(any(), any(), any())).thenReturn(List.of());
        lenient().when(log.save(any(ComplianceReminderLog.class))).thenAnswer(i -> { logged.add(i.getArgument(0)); return i.getArgument(0); });
        lenient().when(settings.findOne(TENANT)).thenReturn(Optional.of(on));
        lenient().when(recipients.findForTenant(TENANT)).thenReturn(List.of());
    }

    private static WorkQueueResponse queueOf(QueueEntry... entries) {
        return new WorkQueueResponse(new Counts(entries.length, entries.length, 0, 0), List.of(entries));
    }

    private static QueueEntry entry(String severity) { return new QueueEntry("DEADLINE", severity, UUID.randomUUID(), "Acme", "VAT return", "Due tomorrow", TODAY.plusDays(1), "deadlines"); }

    private ComplianceReminderRecipient person(boolean adminItems) { return ComplianceReminderRecipient.of(TENANT, PERSON, adminItems, ADMIN); }

    @Test @DisplayName("a digest is handed to the notification service and logged as handed over, not delivered")
    void sends() {
        when(queue.queue(TENANT, false, TODAY)).thenReturn(queueOf(entry("HIGH"), entry("LOW")));
        String outcome = service.sendTo(TENANT, on, person(false), TODAY);
        assertThat(outcome).isEqualTo("HANDED_OVER");
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("Compliance Services: 1 item needs attention (1 urgent)");
        assertThat(sent.getValue().recipients().size()).isEqualTo(1);
        assertThat(logged.get(0).getOutcome()).isEqualTo("HANDED_OVER");
        assertThat(logged.get(0).getItemCount()).isEqualTo(1);
    }

    @Test @DisplayName("nothing is sent when nothing is due, but the day is still recorded")
    void nothingDue() {
        when(queue.queue(TENANT, false, TODAY)).thenReturn(queueOf(entry("LOW")));
        assertThat(service.sendTo(TENANT, on, person(false), TODAY)).isEqualTo("NOTHING_DUE");
        verify(notifications, never()).send(any());
        assertThat(logged.get(0).getOutcome()).isEqualTo("NOTHING_DUE");
    }

    @Test @DisplayName("a person already sent today's digest is not sent another")
    void once() {
        when(log.findForDay(TENANT, PERSON, TODAY)).thenReturn(List.of(ComplianceReminderLog.of(TENANT, PERSON, TODAY, 1, "HANDED_OVER", null)));
        assertThat(service.sendTo(TENANT, on, person(false), TODAY)).isEqualTo(ComplianceReminderService.ALREADY_DONE);
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("administrator-only items are included only for people chosen to receive them")
    void adminItems() {
        when(queue.queue(TENANT, true, TODAY)).thenReturn(queueOf(entry("HIGH")));
        assertThat(service.sendTo(TENANT, on, person(true), TODAY)).isEqualTo("HANDED_OVER");
        verify(queue).queue(TENANT, true, TODAY);
        verify(queue, never()).queue(eq(TENANT), eq(false), any());
    }

    @Test @DisplayName("a person who can no longer be found is recorded, not sent to")
    void gone() {
        when(resolver.resolveUser(TENANT, PERSON)).thenReturn(Optional.empty());
        assertThat(service.sendTo(TENANT, on, person(false), TODAY)).isEqualTo("NO_ADDRESS");
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("the digest cannot be switched on with nobody to send it to, or to someone who does not exist")
    void updateRefused() {
        assertThatThrownBy(() -> service.update(TENANT, new SettingsRequest(true, "HIGH", 25, List.of()), ADMIN)).isInstanceOf(HandyFlowException.class);
        UUID stranger = UUID.randomUUID();
        when(resolver.resolveUser(TENANT, stranger)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(TENANT, new SettingsRequest(true, "HIGH", 25, List.of(new RecipientRequest(stranger, false))), ADMIN)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.update(TENANT, new SettingsRequest(true, "HIGH", 25, List.of(new RecipientRequest(PERSON, false), new RecipientRequest(PERSON, true))), ADMIN)).isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("updating keeps people who stay, adds new ones and removes the rest")
    void updateRecipients() {
        UUID stays = UUID.randomUUID();
        UUID leaves = UUID.randomUUID();
        UUID joins = UUID.randomUUID();
        ComplianceReminderRecipient keep = ComplianceReminderRecipient.of(TENANT, stays, false, ADMIN);
        ComplianceReminderRecipient drop = ComplianceReminderRecipient.of(TENANT, leaves, false, ADMIN);
        when(recipients.findForTenant(TENANT)).thenReturn(List.of(keep, drop));
        service.update(TENANT, new SettingsRequest(true, "MEDIUM", 20, List.of(new RecipientRequest(stays, true), new RecipientRequest(joins, false))), ADMIN);
        assertThat(keep.isAdminItems()).isTrue();
        verify(recipients).deleteAll(any());
        ArgumentCaptor<ComplianceReminderRecipient> saved = ArgumentCaptor.forClass(ComplianceReminderRecipient.class);
        verify(recipients, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().stream().anyMatch(r -> r.getUserId().equals(joins))).isTrue();
        assertThat(on.getMinSeverity()).isEqualTo("MEDIUM");
    }

    @Test @DisplayName("a preview shows each person what they would get and sends nothing")
    void preview() {
        when(recipients.findForTenant(TENANT)).thenReturn(List.of(person(false)));
        when(queue.queue(TENANT, false, TODAY)).thenReturn(queueOf(entry("HIGH")));
        List<PreviewView> p = service.preview(TENANT, TODAY);
        assertThat(p.size()).isEqualTo(1);
        assertThat(p.get(0).itemCount()).isEqualTo(1);
        assertThat(p.get(0).body().contains("VAT return")).isTrue();
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("send now needs the digest switched on, and tallies each outcome")
    void runner() {
        ComplianceReminderRunner runner = new ComplianceReminderRunner(settings, recipients, service);
        ComplianceReminderSettings off = ComplianceReminderSettings.blank(TENANT);
        when(settings.findOne(TENANT)).thenReturn(Optional.of(off));
        assertThatThrownBy(() -> runner.run(TENANT, TODAY)).isInstanceOf(HandyFlowException.class);

        when(settings.findOne(TENANT)).thenReturn(Optional.of(on));
        when(recipients.findForTenant(TENANT)).thenReturn(List.of(person(false)));
        when(queue.queue(TENANT, false, TODAY)).thenReturn(queueOf(entry("HIGH")));
        RunResult r = runner.run(TENANT, TODAY);
        assertThat(r.handedOver()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(0);
    }
}
