package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOnboardingRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOnboardingTaskRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Onboarding reminders go through the ledger: the same words reach the same person once a day, however many times the job runs. */
class OnboardingReminderLedgerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final HrEmployee employee = HrEmployee.create(tenant, "E-1", "Thandi", "Mokoena", TODAY.minusDays(20), "PERMANENT", new BigDecimal("20000"), "MONTHLY");
    private final HrOnboarding onboarding = HrOnboarding.start(tenant.getValue(), employee.getId(), TODAY.minusDays(20), TODAY.plusMonths(2), UUID.randomUUID());
    private final List<NotificationRequest> sent = new ArrayList<>();
    private final Set<String> claims = new HashSet<>();
    private final List<String> released = new ArrayList<>();
    private boolean failSending, ledgerDown;
    private final List<HrOnboardingTask> tasks = new ArrayList<>();

    private final HrOnboardingRepository onboardings = (HrOnboardingRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HrOnboardingRepository.class}, (p, m, a) -> {
                if (m.getName().equals("allInProgress")) return List.of(onboarding);
                throw new UnsupportedOperationException(m.getName());
            });
    private final HrOnboardingTaskRepository taskRepo = (HrOnboardingTaskRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HrOnboardingTaskRepository.class}, (p, m, a) -> {
                if (m.getName().equals("forOnboardings")) return tasks;
                throw new UnsupportedOperationException(m.getName());
            });
    private final HrEmployeeRepository employees = (HrEmployeeRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HrEmployeeRepository.class}, (p, m, a) -> {
                if (m.getName().equals("findAllById")) return List.of(employee);
                throw new UnsupportedOperationException(m.getName());
            });
    private final NotificationService notifications = new NotificationService(null, null) {
        @Override public void send(NotificationRequest r) {
            if (failSending) throw new IllegalStateException("mail down");
            sent.add(r);
        }
    };
    private final TenantAdminRecipients admins = id -> List.of(Recipient.external("HR Admin", "hr@acme.test", null));
    private final UserRecipientResolver users = (t, u) -> Optional.empty();
    private final ReminderLedger ledger = new ReminderLedger(null) {
        @Override public boolean claim(TenantId t, LocalDate day, String digest) {
            if (ledgerDown) throw new IllegalStateException("db");
            return claims.add(day + digest);
        }
        @Override public void release(TenantId t, LocalDate day, String digest) { claims.remove(day + digest); released.add(digest); }
    };
    private final OnboardingReminderService service = new OnboardingReminderService(onboardings, taskRepo, employees, notifications, admins, users, ledger);

    private void lateTask(String title) {
        tasks.add(HrOnboardingTask.of(tenant.getValue(), onboarding.getId(),
                new OnboardingRules.Spec(title, "", "DOCUMENTS", true, 0, OnboardingRules.START, null), TODAY.minusDays(1), 0));
    }

    @Test
    void aSecondRunTheSameDayStaysQuiet() {
        lateTask("Bank details recorded");
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
        assertThat(service.remindAll(TODAY)).isEqualTo(0);
        assertThat(sent).hasSize(1);
    }

    @Test
    void aSecondAppInstanceSharingTheLedgerStaysQuiet() {
        lateTask("Bank details recorded");
        OnboardingReminderService other = new OnboardingReminderService(onboardings, taskRepo, employees, notifications, admins, users, ledger);
        service.remindAll(TODAY);
        assertThat(other.remindAll(TODAY)).isEqualTo(0);
        assertThat(sent).hasSize(1);
    }

    @Test
    void differentWordsOnAnotherDayAreSentAgain() {
        lateTask("Bank details recorded");
        service.remindAll(TODAY);
        // Six days later the task is seven days late, so the words differ and the reminder is due again.
        tasks.clear();
        tasks.add(HrOnboardingTask.of(tenant.getValue(), onboarding.getId(),
                new OnboardingRules.Spec("Bank details recorded", "", "DOCUMENTS", true, 0, OnboardingRules.START, null), TODAY.minusDays(1), 0));
        assertThat(service.remindAll(TODAY.plusDays(6))).isEqualTo(1);
        assertThat(sent).hasSize(2);
    }

    @Test
    void theClaimIsGivenBackWhenSendingFails() {
        lateTask("Bank details recorded");
        failSending = true;
        assertThat(service.remindAll(TODAY)).isEqualTo(0);
        assertThat(released).hasSize(1);
        failSending = false;
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
    }

    @Test
    void aLedgerThatIsDownNeverStopsTheReminder() {
        lateTask("Bank details recorded");
        ledgerDown = true;
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
        assertThat(sent).hasSize(1);
    }
}
