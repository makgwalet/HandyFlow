package za.co.handyflow.platform.training.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.application.internal.TrainingManagerAlerts.Case;
import za.co.handyflow.platform.training.domain.model.CertificateDigestRules.Item;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingRequirement;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCertificateRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingRequirementRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Cell;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Matrix;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.OpenSession;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Row;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Team;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The required-training matrix, the manager's team view, self-booking and the manager alerts, run against in-memory repositories so the
 * real services, rules and notifier are exercised together.
 */
class TrainingTeamAndMatrixTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    private static final Object NOPE = new Object();

    private final TenantId tenant = TenantId.generate();
    private final UUID bossUser = UUID.randomUUID(), thandiUser = UUID.randomUUID(), outsiderUser = UUID.randomUUID();

    private final EmployeeSummary boss = emp("B1", "Boss Ndlovu", "Manager", "Admin", "ACTIVE", null, bossUser);
    private final EmployeeSummary thandi = emp("E1", "Thandi Nkosi", "Forklift Operator", "Warehouse", "ACTIVE", boss.id(), thandiUser);
    private final EmployeeSummary sipho = emp("E2", "Sipho Dube", "Clerk", "Admin", "ACTIVE", boss.id(), UUID.randomUUID());
    private final EmployeeSummary lerato = emp("E3", "Lerato Mokoena", "forklift  operator", "Warehouse", "ACTIVE", null, UUID.randomUUID());
    private final List<EmployeeSummary> staff = new ArrayList<>(List.of(boss, thandi, sipho, lerato));

    private final TrainingCourse firstAid = course("FA", "First Aid", true);
    private final TrainingCourse forklift = course("FL", "Forklift Licence", true);
    private final TrainingCourse induction = course("IN", "Induction", false);
    private final List<TrainingCourse> courses = new ArrayList<>(List.of(firstAid, forklift, induction));
    private final List<TrainingSession> sessions = new ArrayList<>();
    private final List<TrainingEnrollment> enrollments = new ArrayList<>();
    private final List<TrainingCertificate> certificates = new ArrayList<>();
    private final List<TrainingRequirement> requirements = new ArrayList<>();
    private final List<NotificationRequest> sent = new ArrayList<>();

    private EmployeeSummary emp(String number, String name, String title, String dept, String status, UUID manager, UUID user) {
        String[] n = name.split(" ");
        return new EmployeeSummary(UUID.randomUUID(), number, n[0], n[1], name, name.split(" ")[0].toLowerCase() + "@acme.test", null, title, dept, status, manager, user);
    }

    private TrainingCourse course(String code, String title, boolean certificate) {
        return TrainingCourse.create(tenant, code, title, null, "Safety", "IN_PERSON", null, null, null, certificate, certificate ? 24 : null);
    }

    // ── in-memory repositories ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(TrainingTeamAndMatrixTest.class.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }

    private static boolean has(Iterable<?> ids, Object id) {
        for (Object x : ids) if (x.equals(id)) return true;
        return false;
    }

    private final TrainingRequirementRepository requirementRepo = fake(TrainingRequirementRepository.class, (n, a) -> switch (n) {
        case "findAllForTenant" -> List.copyOf(requirements);
        case "findByTenantAndId" -> requirements.stream().filter(r -> r.getId().equals(a[1])).findFirst();
        case "save" -> { requirements.add((TrainingRequirement) a[0]); yield a[0]; }
        case "delete" -> { requirements.remove(a[0]); yield null; }
        default -> NOPE;
    });
    private final TrainingCourseRepository courseRepo = fake(TrainingCourseRepository.class, (n, a) -> switch (n) {
        case "findAllById" -> courses.stream().filter(c -> has((Iterable<?>) a[0], c.getId())).toList();
        case "findActiveById" -> courses.stream().filter(c -> c.getId().equals(a[1])).findFirst();
        default -> NOPE;
    });
    private final TrainingSessionRepository sessionRepo = fake(TrainingSessionRepository.class, (n, a) -> switch (n) {
        case "findAllById" -> sessions.stream().filter(s -> has((Iterable<?>) a[0], s.getId())).toList();
        case "findByTenantAndId", "findByTenantAndIdForUpdate" -> sessions.stream().filter(s -> s.getId().equals(a[1])).findFirst();
        case "findAll" -> new PageImpl<>(sessions.stream().filter(s -> a[2] == null || a[2].equals(s.getStatus())).toList());
        default -> NOPE;
    });
    private final TrainingEnrollmentRepository enrollmentRepo = fake(TrainingEnrollmentRepository.class, (n, a) -> switch (n) {
        case "findAllForEmployees" -> enrollments.stream().filter(e -> has((Iterable<?>) a[1], e.getEmployeeId())).toList();
        case "findAllForEmployee" -> enrollments.stream().filter(e -> e.getEmployeeId().equals(a[1])).toList();
        case "findActiveEnrollment" -> enrollments.stream().filter(e -> e.getSessionId().equals(a[1]) && e.getEmployeeId().equals(a[2]) && !"CANCELLED".equals(e.getStatus())).findFirst();
        case "findByTenantAndId" -> enrollments.stream().filter(e -> e.getId().equals(a[1])).findFirst();
        case "countLiveBySession" -> enrollments.stream().filter(e -> e.getSessionId().equals(a[1]) && List.of("ENROLLED", "ATTENDED", "COMPLETED").contains(e.getStatus())).count();
        case "save" -> { if (!enrollments.contains(a[0])) enrollments.add((TrainingEnrollment) a[0]); yield a[0]; }
        default -> NOPE;
    });
    private final TrainingCertificateRepository certificateRepo = fake(TrainingCertificateRepository.class, (n, a) -> switch (n) {
        case "findAllForEmployees" -> certificates.stream().filter(c -> has((Iterable<?>) a[1], c.getEmployeeId())).toList();
        default -> NOPE;
    });

    private final HrFacade hr = new HrFacade(null) {
        @Override public Optional<EmployeeSummary> findEmployeeById(TenantId t, UUID id) { return staff.stream().filter(e -> e.id().equals(id)).findFirst(); }
        @Override public Optional<EmployeeSummary> findLinkedEmployee(TenantId t, UUID id) { return findEmployeeById(t, id); }
        @Override public Optional<EmployeeSummary> findEmployeeByUserId(TenantId t, UUID user) {
            // As in HR itself, the lookup by login carries no reporting line.
            return staff.stream().filter(e -> user.equals(e.userId())).findFirst()
                    .map(e -> new EmployeeSummary(e.id(), e.employeeNumber(), e.firstName(), e.lastName(), e.fullName(), e.email(), e.phone(), e.jobTitle(), e.department(), e.status()));
        }
        @Override public List<EmployeeSummary> activeEmployees(TenantId t) { return staff.stream().filter(e -> !"TERMINATED".equals(e.status())).toList(); }
        @Override public List<EmployeeSummary> directReports(TenantId t, UUID manager) { return activeEmployees(t).stream().filter(e -> manager.equals(e.managerId())).toList(); }
    };

    private final NotificationService notifications = new NotificationService(null, null) {
        @Override public void send(NotificationRequest r) { sent.add(r); }
    };

    private final TrainingPicture picture = new TrainingPicture(enrollmentRepo, sessionRepo, courseRepo, certificateRepo, requirementRepo);
    private final TrainingRequirementService requirementService = new TrainingRequirementService(requirementRepo, courseRepo, hr, picture);
    private final TrainingTeamService teamService = new TrainingTeamService(hr, picture);
    private final TrainingNotifier notifier = new TrainingNotifier(notifications, hr, courseRepo);
    private final TrainingEnrollmentService enrollmentService = new TrainingEnrollmentService(enrollmentRepo, sessionRepo, hr, notifier);
    private final TrainingSelfBookingService booking = new TrainingSelfBookingService(hr, sessionRepo, courseRepo, enrollmentRepo, requirementRepo, enrollmentService, notifier);
    private final TrainingManagerAlerts alerts = new TrainingManagerAlerts(hr, notifications);

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private TrainingSession session(TrainingCourse c, int startInDays, Integer capacity, boolean open) {
        TrainingSession s = TrainingSession.create(tenant, c.getId(), TODAY.plusDays(startInDays), TODAY.plusDays(startInDays + 1), "Head Office", "Trainer", capacity, null);
        if (open) s.setOpenBooking(true);
        sessions.add(s);
        return s;
    }

    /** The person attended a session of the course and was given a certificate that runs out on {@code expiry}. */
    private TrainingCertificate certify(EmployeeSummary e, TrainingCourse c, LocalDate expiry) {
        TrainingSession s = session(c, -60, null, false);
        TrainingEnrollment en = TrainingEnrollment.create(tenant, s.getId(), e.id(), e.fullName(), e.employeeNumber(), null);
        enrollments.add(en);
        TrainingCertificate cert = TrainingCertificate.create(tenant, en.getId(), e.id(), e.fullName(), c.getTitle(), "C-" + certificates.size(), TODAY.minusDays(60), expiry);
        certificates.add(cert);
        return cert;
    }

    private void require(TrainingCourse c, String title, String dept) { requirements.add(TrainingRequirement.create(tenant, c.getId(), title, dept, null)); }

    private Row row(Matrix m, EmployeeSummary e) { return m.rows().stream().filter(r -> r.employeeId().equals(e.id())).findFirst().orElse(null); }

    private Cell cell(Matrix m, EmployeeSummary e, TrainingCourse c) {
        int col = -1;
        for (int i = 0; i < m.columns().size(); i++) if (m.columns().get(i).courseId().equals(c.getId())) col = i;
        return row(m, e).cells().get(col);
    }

    // ── requirements and the matrix ─────────────────────────────────────────────────────────────

    @Test
    void aRequirementNeedsACourseThatGivesACertificate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> requirementService.add(tenant, induction.getId(), null, null, null));
        assertTrue(ex.getMessage().contains("certificate"));
        assertThrows(IllegalArgumentException.class, () -> requirementService.add(tenant, UUID.randomUUID(), null, null, null));
    }

    @Test
    void theSameRequirementCannotBeSetTwiceWhateverTheCapitals() {
        requirementService.add(tenant, forklift.getId(), "Forklift Operator", "Warehouse", null);
        assertThrows(IllegalStateException.class, () -> requirementService.add(tenant, forklift.getId(), "  forklift   operator ", "WAREHOUSE", null));
        // The same course for a different role is a different requirement.
        requirementService.add(tenant, forklift.getId(), "Supervisor", null, null);
        assertEquals(2, requirementService.list(tenant).size());
        assertEquals("Forklift Operator in Warehouse", requirementService.list(tenant).get(0).appliesTo());
    }

    @Test
    void removingARequirementRemovesIt() {
        UUID id = requirementService.add(tenant, firstAid.getId(), null, null, null).id();
        requirementService.remove(tenant, id);
        assertTrue(requirementService.list(tenant).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> requirementService.remove(tenant, id));
    }

    @Test
    void theMatrixShowsWhereEachPersonStands() {
        require(firstAid, null, null);                   // everyone
        require(forklift, "Forklift Operator", null);    // forklift operators only
        certify(thandi, firstAid, TODAY.plusDays(200));
        certify(thandi, forklift, TODAY.plusDays(12));   // expiring
        certify(lerato, forklift, TODAY.minusDays(3));   // lapsed
        TrainingSession coming = session(firstAid, 20, 10, false);
        enrollments.add(TrainingEnrollment.create(tenant, coming.getId(), lerato.id(), lerato.fullName(), "E3", null));   // booked for first aid

        Matrix m = requirementService.matrix(tenant);

        assertEquals(List.of("First Aid", "Forklift Licence"), m.columns().stream().map(c -> c.courseTitle()).toList());
        assertEquals("OK", cell(m, thandi, firstAid).status());
        assertEquals("EXPIRING", cell(m, thandi, forklift).status());
        assertEquals("MISSING", cell(m, sipho, firstAid).status());
        assertNull(cell(m, sipho, forklift));            // not required of a clerk
        assertEquals("EXPIRED", cell(m, lerato, forklift).status());
        assertEquals("BOOKED", cell(m, lerato, firstAid).status());
        // Four people, each with first aid required, plus two forklift operators.
        assertEquals(4, m.summary().people());
        assertEquals(6, m.summary().required());
        assertEquals(1, m.summary().valid());
        assertEquals(1, m.summary().expiring());
        assertEquals(1, m.summary().expired());
        assertEquals(2, m.summary().missing());          // boss and Sipho have no first aid
        assertEquals(1, m.summary().booked());
    }

    @Test
    void aWithdrawnCertificateDoesNotCount() {
        require(firstAid, null, null);
        TrainingCertificate c = certify(sipho, firstAid, null);
        c.revoke("Issued in error");
        assertEquals("MISSING", cell(requirementService.matrix(tenant), sipho, firstAid).status());
    }

    @Test
    void peopleWhoHaveLeftAreNotInTheMatrix() {
        require(firstAid, null, null);
        staff.set(2, emp("E2", "Sipho Dube", "Clerk", "Admin", "TERMINATED", boss.id(), null));
        assertNull(row(requirementService.matrix(tenant), staff.get(2)));
    }

    @Test
    void aCourseNobodyIsRequiredToHoldIsNotAColumn() {
        require(firstAid, "Pilot", null);                // nobody is a pilot
        Matrix m = requirementService.matrix(tenant);
        assertTrue(m.rows().isEmpty());
        assertEquals(0, m.summary().required());
    }

    // ── the manager's team ──────────────────────────────────────────────────────────────────────

    @Test
    void aManagerSeesOnlyTheirDirectReports() {
        require(firstAid, null, null);
        certify(thandi, firstAid, TODAY.plusDays(5));
        Team team = teamService.team(tenant, bossUser);
        assertTrue(team.linked());
        assertEquals(List.of("Sipho Dube", "Thandi Nkosi"), team.members().stream().map(x -> x.name()).toList());
        var t = team.members().get(1);
        assertEquals("EXPIRING", t.required().get(0).status());
        assertEquals(1, t.certificates().size());
        assertEquals("EXPIRING", t.certificates().get(0).state());
        assertEquals("MISSING", team.members().get(0).required().get(0).status());
    }

    @Test
    void someoneWithNoReportsSeesNothingAndSomeoneUnlinkedIsToldSo() {
        assertTrue(teamService.team(tenant, thandiUser).members().isEmpty());
        assertTrue(teamService.team(tenant, thandiUser).linked());
        Team nobody = teamService.team(tenant, outsiderUser);
        assertFalse(nobody.linked());
        assertTrue(nobody.members().isEmpty());
    }

    // ── booking yourself on ─────────────────────────────────────────────────────────────────────

    @Test
    void onlyOpenComingSessionsWithRoomAreOffered() {
        require(firstAid, null, null);
        TrainingSession good = session(firstAid, 10, 5, true);
        session(firstAid, 12, 5, false);                              // not opened for self-booking
        session(firstAid, -2, 5, true);                               // already started
        TrainingSession full = session(firstAid, 14, 1, true);
        enrollments.add(TrainingEnrollment.create(tenant, full.getId(), sipho.id(), "S", "E2", null));
        TrainingSession mine = session(forklift, 15, 5, true);
        enrollments.add(TrainingEnrollment.create(tenant, mine.getId(), thandi.id(), "T", "E1", null));   // already booked

        List<OpenSession> open = booking.open(tenant, thandiUser);

        assertEquals(1, open.size());
        assertEquals(good.getId(), open.get(0).id());
        assertEquals(Integer.valueOf(5), open.get(0).seatsLeft());
        assertTrue(open.get(0).required());
    }

    @Test
    void bookingPutsThePersonOnTheSessionAndTellsTheirManager() {
        TrainingSession s = session(firstAid, 10, 5, true);
        booking.book(tenant, thandiUser, s.getId());

        assertEquals(1, enrollments.size());
        assertEquals(thandi.id(), enrollments.get(0).getEmployeeId());
        NotificationRequest toManager = sent.stream().filter(r -> r.recipients().stream().anyMatch(x -> bossUser.equals(x.userId()))).findFirst().orElseThrow();
        assertTrue(toManager.message().contains("Thandi Nkosi booked themselves onto \"First Aid\""));
        // And the employee gets the usual confirmation.
        assertTrue(sent.stream().anyMatch(r -> r.recipients().stream().anyMatch(x -> "thandi@acme.test".equals(x.email()))));
    }

    @Test
    void aSessionNotOpenedForSelfBookingCannotBeBooked() {
        TrainingSession s = session(firstAid, 10, 5, false);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> booking.book(tenant, thandiUser, s.getId()));
        assertTrue(ex.getMessage().contains("not open"));
        assertTrue(enrollments.isEmpty());
    }

    @Test
    void theLastPlaceGoesToOnePersonOnly() {
        TrainingSession s = session(firstAid, 10, 1, true);
        booking.book(tenant, thandiUser, s.getId());
        UUID siphoUser = sipho.userId();
        assertEquals("This session is full.", assertThrows(IllegalStateException.class, () -> booking.book(tenant, siphoUser, s.getId())).getMessage());
        assertEquals("You are already booked on this session.", assertThrows(IllegalStateException.class, () -> booking.book(tenant, thandiUser, s.getId())).getMessage());
    }

    @Test
    void aLoginWithNoEmployeeRecordCannotBook() {
        TrainingSession s = session(firstAid, 10, 5, true);
        assertThrows(ResourceNotFoundException.class, () -> booking.book(tenant, outsiderUser, s.getId()));
    }

    @Test
    void aPersonCanTakeThemselvesOffUntilTheSessionStarts() {
        TrainingSession s = session(firstAid, 10, 5, true);
        booking.book(tenant, thandiUser, s.getId());
        sent.clear();
        booking.cancel(tenant, thandiUser, enrollments.get(0).getId());
        assertEquals("CANCELLED", enrollments.get(0).getStatus());
        assertTrue(sent.get(0).message().contains("cancelled their booking"));
    }

    @Test
    void anotherPersonsBookingLooksLikeOneThatDoesNotExist() {
        TrainingSession s = session(firstAid, 10, 5, true);
        booking.book(tenant, thandiUser, s.getId());
        assertThrows(ResourceNotFoundException.class, () -> booking.cancel(tenant, sipho.userId(), enrollments.get(0).getId()));
        assertEquals("ENROLLED", enrollments.get(0).getStatus());
    }

    @Test
    void aBookingCannotBeCancelledOnceTheSessionHasStarted() {
        TrainingSession s = session(firstAid, 0, 5, true);       // starts today
        enrollments.add(TrainingEnrollment.create(tenant, s.getId(), thandi.id(), "T", "E1", null));
        assertThrows(IllegalStateException.class, () -> booking.cancel(tenant, thandiUser, enrollments.get(0).getId()));
    }

    // ── managers told about expiring certificates ───────────────────────────────────────────────

    private Case caseOf(EmployeeSummary e, long days) {
        return new Case(e.id(), new Item(e.fullName(), "First Aid", TODAY.plusDays(days), days));
    }

    @Test
    void aManagerGetsOneMessageForTheirWholeTeam() {
        alerts.tell(tenant, List.of(caseOf(thandi, 14), caseOf(sipho, 7), caseOf(lerato, 7)), List.of());

        assertEquals(1, sent.size());                      // Lerato has no manager
        NotificationRequest r = sent.get(0);
        assertEquals(bossUser, r.recipients().get(0).userId());
        assertEquals("2 certificates in your team are about to expire", r.title());
        assertTrue(r.message().contains("Thandi Nkosi: First Aid expires in 14 days"));
        assertTrue(r.message().contains("Sipho Dube: First Aid expires in 7 days"));
        assertFalse(r.message().contains("Lerato"));
    }

    @Test
    void expiredAndExpiringAreSeparateMessages() {
        alerts.tell(tenant, List.of(caseOf(thandi, 1)), List.of(caseOf(sipho, -1)));
        assertEquals(2, sent.size());
        assertTrue(sent.get(0).title().contains("about to expire"));
        assertTrue(sent.get(1).title().contains("has expired"));
    }

    @Test
    void aManagerWhoHasLeftIsNotMessaged() {
        staff.set(0, emp("B1", "Boss Ndlovu", "Manager", "Admin", "TERMINATED", null, bossUser));
        // The staff list changed, so rebuild the reports with the same manager id.
        EmployeeSummary t = new EmployeeSummary(thandi.id(), "E1", "Thandi", "Nkosi", "Thandi Nkosi", null, null, "X", "Y", "ACTIVE", staff.get(0).id(), null);
        staff.set(1, t);
        alerts.tell(tenant, List.of(caseOf(t, 7)), List.of());
        assertTrue(sent.isEmpty());
    }
}
