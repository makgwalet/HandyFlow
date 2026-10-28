package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Warns HR and the employee when a work permit or passport is running out, once a day, with nothing but names and dates. */
class ForeignIdentityReminderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    private final List<HrEmployee> people = new ArrayList<>();
    private final List<NotificationRequest> sent = new ArrayList<>();
    private final Set<String> claims = new HashSet<>();
    private final List<String> released = new ArrayList<>();
    private LocalDate askedLimit;
    private boolean failSending;
    private boolean ledgerDown;

    private final HrEmployeeRepository employees = (HrEmployeeRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HrEmployeeRepository.class}, (p, m, a) -> {
                if (m.getName().equals("withDocumentsExpiringBy")) { askedLimit = (LocalDate) a[0]; return people; }
                throw new UnsupportedOperationException(m.getName());
            });

    private final NotificationService notifications = new NotificationService(null, null) {
        @Override public void send(NotificationRequest r) {
            if (failSending) throw new IllegalStateException("mail down");
            sent.add(r);
        }
    };

    private final TenantAdminRecipients admins = id -> List.of(Recipient.external("HR Admin", "hr@acme.test", null));
    private final Map<UUID, Recipient> logins = new HashMap<>();
    private final UserRecipientResolver users = (t, u) -> Optional.ofNullable(logins.get(u));

    private final ReminderLedger ledger = new ReminderLedger(null) {
        @Override public boolean claim(TenantId t, LocalDate day, String digest) {
            if (ledgerDown) throw new IllegalStateException("db");
            return claims.add(day + digest);
        }
        @Override public void release(TenantId t, LocalDate day, String digest) { claims.remove(day + digest); released.add(digest); }
    };

    private final ForeignIdentityReminderService service = new ForeignIdentityReminderService(employees, notifications, admins, users, ledger);

    private HrEmployee person(String first, LocalDate permitExpiry, LocalDate passportExpiry, boolean login) {
        HrEmployee e = HrEmployee.create(tenant, "E-" + first, first, "Moyo", TODAY.minusYears(1), "PERMANENT", new BigDecimal("20000"), "MONTHLY");
        e.setWorkPermit(permitExpiry == null ? null : "GENERAL_WORK", permitExpiry == null ? null : "VISA-SECRET-123", permitExpiry);
        e.setPassport(passportExpiry == null ? null : "PASS-SECRET-9", passportExpiry == null ? null : "ZW", passportExpiry);
        if (login) {
            UUID user = UUID.randomUUID();
            e.setUserId(user);
            logins.put(user, Recipient.user(user, first, first.toLowerCase() + "@acme.test", null));
        }
        people.add(e);
        return e;
    }

    @Test
    void looksNinetyDaysAhead() {
        service.remindAll(TODAY);
        assertThat(askedLimit).isEqualTo(TODAY.plusDays(90));
    }

    @Test
    void aPermitAtAReminderDayTellsTheEmployeeAndHr() {
        person("Tendai", TODAY.plusDays(30), null, true);
        assertThat(service.remindAll(TODAY)).isEqualTo(2);
        assertThat(sent).hasSize(2);
        assertThat(sent).allMatch(r -> r.type() == NotificationType.WORK_PERMIT_EXPIRING);
        NotificationRequest mine = sent.stream().filter(r -> r.recipients().get(0).email().equals("tendai@acme.test")).findFirst().orElseThrow();
        assertThat(mine.message()).contains("Your work permit expires in 30 days");
        NotificationRequest hr = sent.stream().filter(r -> r.recipients().get(0).email().equals("hr@acme.test")).findFirst().orElseThrow();
        assertThat(hr.message()).contains("Tendai Moyo: work permit expires in 30 days");
    }

    @Test
    void noMessageCarriesADocumentNumber() {
        person("Tendai", TODAY.plusDays(30), TODAY.plusDays(7), true);
        service.remindAll(TODAY);
        assertThat(sent).isNotEmpty();
        assertThat(sent).noneMatch(r -> r.message().contains("SECRET") || r.title().contains("SECRET"));
    }

    @Test
    void anEmployeeWithoutALoginIsNotMessagedButHrStillHears() {
        person("Tendai", TODAY.plusDays(14), null, false);
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
        assertThat(sent.get(0).recipients().get(0).email()).isEqualTo("hr@acme.test");
    }

    @Test
    void aDocumentBetweenReminderDaysSendsNothing() {
        person("Tendai", TODAY.plusDays(45), TODAY.plusDays(80), true);
        assertThat(service.remindAll(TODAY)).isEqualTo(0);
        assertThat(sent).isEmpty();
    }

    @Test
    void aSecondRunTheSameDaySendsNothingMore() {
        person("Tendai", TODAY.plusDays(30), null, true);
        service.remindAll(TODAY);
        int first = sent.size();
        assertThat(service.remindAll(TODAY)).isEqualTo(0);
        assertThat(sent).hasSize(first);
    }

    @Test
    void theNextDayCanSendAgainWhenItIsAReminderDayAgain() {
        person("Tendai", TODAY.plusDays(30), null, true);
        service.remindAll(TODAY);
        int first = sent.size();
        service.remindAll(TODAY.plusDays(16));      // 14 days left
        assertThat(sent.size()).isGreaterThan(first);
    }

    @Test
    void oneListForManyPeople() {
        person("Tendai", TODAY.plusDays(30), null, false);
        person("Grace", TODAY.minusDays(1), null, false);
        service.remindAll(TODAY);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).title()).isEqualTo("Work permits and passports needing attention (2)");
        assertThat(sent.get(0).message()).contains("Grace Moyo: work permit expired 1 day ago").contains("may not lawfully keep working");
    }

    @Test
    void theClaimIsGivenBackWhenSendingFailsSoTheNextRunCanTryAgain() {
        person("Tendai", TODAY.plusDays(14), null, false);
        failSending = true;
        assertThat(service.remindAll(TODAY)).isEqualTo(0);
        assertThat(released).isNotEmpty();
        failSending = false;
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
    }

    @Test
    void aLedgerThatIsDownNeverStopsTheWarning() {
        person("Tendai", TODAY.plusDays(14), null, false);
        ledgerDown = true;
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
        assertThat(sent).hasSize(1);
    }

    @Test
    void aCompanyThatFailsDoesNotStopTheOthers() {
        TenantId other = TenantId.of(UUID.randomUUID());
        HrEmployee mine = person("Tendai", TODAY.plusDays(14), null, false);
        HrEmployee theirs = HrEmployee.create(other, "E-9", "Grace", "Banda", TODAY.minusYears(1), "PERMANENT", new BigDecimal("20000"), "MONTHLY");
        theirs.setWorkPermit("GENERAL_WORK", "V-1", TODAY.plusDays(14));
        people.add(theirs);
        TenantAdminRecipients picky = id -> {
            if (id.equals(other)) throw new IllegalStateException("no admins table");
            return List.of(Recipient.external("HR Admin", "hr@acme.test", null));
        };
        ForeignIdentityReminderService s = new ForeignIdentityReminderService(employees, notifications, picky, users, ledger);
        assertThat(s.remindAll(TODAY)).isEqualTo(1);
        assertThat(mine.getFullName()).isEqualTo("Tendai Moyo");
        assertThat(sent).hasSize(1);
    }

    @Test
    void aPersonWithNoDocumentIsNeverDue() {
        HrEmployee none = person("Tendai", null, null, false);
        assertThat(ForeignIdentityReminderService.dueFor(none, TODAY)).isEmpty();
    }
}
