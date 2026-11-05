package za.co.handyflow.platform.invoicing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.InvoiceIssuedEvent;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.RecurringFrequency;
import za.co.handyflow.platform.invoicing.domain.model.RecurringSchedule;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.domain.repository.QuoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.RecurringScheduleRepository;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.usage.UsageCapFacade;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Decision 2: at the hard invoice cap a recurring invoice is delayed (never skipped, never forced) and later issued exactly once. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoicingSchedulerDelayTest {

    private static final ZoneId SA = ZoneId.of("Africa/Johannesburg");

    @Mock QuoteRepository quotes;
    @Mock ApplicationEventPublisher events;
    @Mock RecurringScheduleRepository schedules;
    @Mock InvoiceRepository invoices;
    @Mock InvoiceNumberGenerator numbers;
    @Mock InvoicePaymentTermsResolver terms;
    @Mock StaffNotifier notifier;
    @Mock QuoteService quoteService;
    @Mock EmailService email;
    @Mock CrmFacade crm;
    @Mock InvoicePdfService pdf;
    @Mock TenantFacade tenants;
    @Mock UsageCapFacade cap;
    @Mock RecurringInvoiceQueue queue;
    @Mock PlatformTransactionManager txManager;

    private InvoicingScheduler scheduler;
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private RecurringSchedule schedule;
    private Instant scheduledFor;

    @BeforeEach
    void setUp() {
        scheduler = new InvoicingScheduler(quotes, events, schedules, invoices, numbers, terms, notifier, quoteService, email, crm, pdf, tenants,
                cap, queue, new TransactionTemplate(txManager));
        ReflectionTestUtils.setField(scheduler, "maxDelayDays", 7);
        scheduledFor = Instant.now().minus(1, ChronoUnit.HOURS);
        schedule = RecurringSchedule.create(tenant, null, "Monthly site security", null, RecurringFrequency.MONTHLY, null, null, scheduledFor, null,
                "Walk-in Client", null, null);
        when(schedules.findDueSchedules(any(Instant.class))).thenReturn(List.of(schedule));
        when(schedules.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(queue.queuedIds()).thenReturn(List.of());
        when(numbers.next(tenant)).thenReturn("INV-0099");
        when(terms.resolveDueDate(any(), any(LocalDate.class))).thenReturn(LocalDate.now().plusDays(30));
        when(queue.needingEscalation(any(LocalDate.class), anyInt())).thenReturn(List.of());
    }

    private static UsageCapFacade.Decision full() { return new UsageCapFacade.Decision(true, false, 50, 50, "2026-11"); }
    private static UsageCapFacade.Decision room() { return new UsageCapFacade.Decision(true, true, 10, 50, "2026-11"); }

    private RecurringInvoiceQueue.Entry waiting(LocalDate from, LocalDate to) {
        return new RecurringInvoiceQueue.Entry(UUID.randomUUID(), tenant.getValue(), schedule.getId(), schedule.getTitle(), from, from, to, "QUEUED",
                Instant.now(), 0, null, null, null, null, null);
    }

    @Test
    void atTheCapTheOccurrenceIsQueuedWithItsOriginalDateAndNoInvoiceIsIssued() {
        when(cap.checkAndLock(tenant)).thenReturn(full());
        when(queue.hasQueued(tenant)).thenReturn(false);
        when(queue.enqueue(any(), any(), any(), any(), any())).thenReturn(true);
        Instant lastRunBefore = schedule.getLastRunAt();

        scheduler.fireRecurringSchedules();

        LocalDate expectedDate = scheduledFor.atZone(SA).toLocalDate();
        verify(queue).enqueue(eq(tenant), eq(schedule.getId()), eq(expectedDate), eq(expectedDate), any(LocalDate.class));
        verify(invoices, never()).save(any(Invoice.class));
        verify(events, never()).publishEvent(any(InvoiceIssuedEvent.class));
        verify(notifier).notify(eq(tenant), eq(NotificationType.RECURRING_INVOICE_DELAYED), anyString(), contains("delayed, not skipped"), anyString(), anyString());
        assertThat(schedule.getLastRunAt()).isEqualTo(lastRunBefore);               // nothing ran
        assertThat(schedule.getNextRunAt()).isAfter(scheduledFor.plus(20, ChronoUnit.DAYS)); // moved on to the next occurrence, from the scheduled time
    }

    @Test
    void anOccurrenceWaitsBehindEarlierDelayedOnesEvenWhenThereIsRoom() {
        when(cap.checkAndLock(tenant)).thenReturn(room());
        when(queue.hasQueued(tenant)).thenReturn(true);
        when(queue.enqueue(any(), any(), any(), any(), any())).thenReturn(true);

        scheduler.fireRecurringSchedules();

        verify(queue).enqueue(eq(tenant), eq(schedule.getId()), any(), any(), any());
        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    void withRoomAndNothingWaitingTheInvoiceIsIssuedNormallyAndNothingIsQueued() {
        when(cap.checkAndLock(tenant)).thenReturn(room());
        when(queue.hasQueued(tenant)).thenReturn(false);

        scheduler.fireRecurringSchedules();

        verify(queue, never()).enqueue(any(), any(), any(), any(), any());
        verify(invoices).save(any(Invoice.class));
        verify(events, times(1)).publishEvent(any(InvoiceIssuedEvent.class));
        assertThat(schedule.getLastRunAt()).isNotNull();
    }

    @Test
    void whenTheCapIsNotEnforcedNothingIsEverQueued() {
        when(cap.checkAndLock(tenant)).thenReturn(new UsageCapFacade.Decision(false, true, 0, null, null));
        when(queue.hasQueued(tenant)).thenReturn(true);   // even if something is somehow waiting, nothing new is held back

        scheduler.fireRecurringSchedules();

        verify(queue, never()).enqueue(any(), any(), any(), any(), any());
        verify(invoices).save(any(Invoice.class));
    }

    @Test
    void aDelayedInvoiceIsIssuedOnceWhenThereIsRoomAndCarriesItsServicePeriod() {
        when(schedules.findDueSchedules(any(Instant.class))).thenReturn(List.of());
        var entry = waiting(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        when(queue.queuedIds()).thenReturn(List.of(entry.id()));
        when(queue.lockQueued(entry.id())).thenReturn(Optional.of(entry));
        when(cap.checkAndLock(tenant)).thenReturn(room());
        Instant nextRunBefore = schedule.getNextRunAt();

        scheduler.fireRecurringSchedules();

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Monthly site security (service period 2026-11-01 to 2026-11-30)");
        verify(events, times(1)).publishEvent(any(InvoiceIssuedEvent.class));
        verify(queue).issued(entry.id(), saved.getValue().getId());
        assertThat(schedule.getNextRunAt()).isEqualTo(nextRunBefore);   // issuing a delayed occurrence does not move the schedule again
    }

    @Test
    void aDelayedInvoiceKeepsWaitingWhileThePlanIsStillFullAndNothingIsIssued() {
        when(schedules.findDueSchedules(any(Instant.class))).thenReturn(List.of());
        var entry = waiting(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        when(queue.queuedIds()).thenReturn(List.of(entry.id()));
        when(queue.lockQueued(entry.id())).thenReturn(Optional.of(entry));
        when(cap.checkAndLock(tenant)).thenReturn(full());

        scheduler.fireRecurringSchedules();

        verify(invoices, never()).save(any(Invoice.class));
        verify(queue, never()).issued(any(), any());
        verify(queue).attempted(eq(entry.id()), contains("50 of 50"));
    }

    @Test
    void anOccurrenceThatIsNoLongerWaitingIsNeverIssuedAgain() {
        when(schedules.findDueSchedules(any(Instant.class))).thenReturn(List.of());
        UUID id = UUID.randomUUID();
        when(queue.queuedIds()).thenReturn(List.of(id));
        when(queue.lockQueued(id)).thenReturn(Optional.empty());

        scheduler.fireRecurringSchedules();

        verify(invoices, never()).save(any(Invoice.class));
        verify(cap, never()).checkAndLock(any());
    }

    @Test
    void oneFullBusinessDoesNotBlockOthersButBlocksItsOwnLaterOccurrences() {
        when(schedules.findDueSchedules(any(Instant.class))).thenReturn(List.of());
        var first = waiting(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        var second = waiting(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        when(queue.queuedIds()).thenReturn(List.of(first.id(), second.id()));
        when(queue.lockQueued(first.id())).thenReturn(Optional.of(first));
        when(queue.lockQueued(second.id())).thenReturn(Optional.of(second));
        when(cap.checkAndLock(tenant)).thenReturn(full());

        scheduler.fireRecurringSchedules();

        verify(cap, times(1)).checkAndLock(tenant);   // the second is not even tried: the oldest goes first
        verify(queue, times(1)).attempted(any(), anyString());
        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    void aDelayedInvoiceWhoseScheduleIsPausedStaysVisibleInsteadOfBeingDropped() {
        when(schedules.findDueSchedules(any(Instant.class))).thenReturn(List.of());
        schedule.pause();
        var entry = waiting(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        when(queue.queuedIds()).thenReturn(List.of(entry.id()));
        when(queue.lockQueued(entry.id())).thenReturn(Optional.of(entry));
        when(cap.checkAndLock(tenant)).thenReturn(room());

        scheduler.fireRecurringSchedules();

        verify(invoices, never()).save(any(Invoice.class));
        verify(queue).attempted(eq(entry.id()), contains("paused"));
        verify(queue, never()).issued(any(), any());
    }

    @Test
    void anOccurrenceWaitingLongerThanTheLimitEscalatesOnce() {
        when(schedules.findDueSchedules(any(Instant.class))).thenReturn(List.of());
        var entry = waiting(LocalDate.now(SA).minusDays(12), LocalDate.now(SA).plusDays(17));
        when(queue.needingEscalation(any(LocalDate.class), eq(7))).thenReturn(List.of(entry));

        scheduler.fireRecurringSchedules();

        verify(notifier).notify(eq(TenantId.of(entry.tenantId())), eq(NotificationType.RECURRING_INVOICE_DELAY_ESCALATED), anyString(),
                contains("7-day limit"), anyString(), anyString());
        verify(queue).escalated(entry.id());
    }
}
