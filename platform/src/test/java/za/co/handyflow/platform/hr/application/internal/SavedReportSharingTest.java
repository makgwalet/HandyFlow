package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrSavedReport;
import za.co.handyflow.platform.hr.domain.model.HrSavedReportShare;
import za.co.handyflow.platform.hr.domain.repository.HrSavedReportRepository;
import za.co.handyflow.platform.hr.domain.repository.HrSavedReportShareRepository;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.Colleague;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.SaveRequest;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.View;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Editing and sharing saved reports, against in-memory repositories. */
class SavedReportSharingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final Set<String> READER = Set.of("HR_REPORT_READ");
    private static final Set<String> PAY = Set.of("HR_REPORT_READ", "HR_COMPENSATION_READ");

    private final TenantId tenant = TenantId.generate();
    private final UUID ann = UUID.randomUUID(), ben = UUID.randomUUID(), cleo = UUID.randomUUID(), dan = UUID.randomUUID();
    private final Map<UUID, Set<String>> rights = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<HrSavedReport> reports = new ArrayList<>();
    private final List<HrSavedReportShare> shares = new ArrayList<>();

    private static final Object NOPE = new Object();

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(SavedReportSharingTest.class.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }

    private final HrSavedReportRepository repo = fake(HrSavedReportRepository.class, (n, a) -> switch (n) {
        case "forOwner" -> reports.stream().filter(r -> r.getOwnerId().equals(a[1])).toList();
        case "findForOwner" -> reports.stream().filter(r -> r.getOwnerId().equals(a[1]) && r.getId().equals(a[2])).findFirst();
        case "countForOwner" -> reports.stream().filter(r -> r.getOwnerId().equals(a[1])).count();
        case "save" -> { if (!reports.contains(a[0])) reports.add((HrSavedReport) a[0]); yield a[0]; }
        case "findAllById" -> reports.stream().filter(r -> ((Iterable<?>) a[0]).spliterator() != null && contains((Iterable<?>) a[0], r.getId())).toList();
        default -> NOPE;
    });

    private static boolean contains(Iterable<?> it, Object o) {
        for (Object x : it) if (x.equals(o)) return true;
        return false;
    }

    private final HrSavedReportShareRepository shareRepo = fake(HrSavedReportShareRepository.class, (n, a) -> switch (n) {
        case "forReport" -> shares.stream().filter(s -> s.getReportId().equals(a[0])).toList();
        case "forReports" -> shares.stream().filter(s -> contains((Iterable<?>) a[0], s.getReportId())).toList();
        case "forUser" -> shares.stream().filter(s -> s.getUserId().equals(a[1])).toList();
        case "findForUser" -> shares.stream().filter(s -> s.getUserId().equals(a[1]) && s.getReportId().equals(a[2])).findFirst();
        case "save" -> { shares.add((HrSavedReportShare) a[0]); yield a[0]; }
        case "delete" -> { shares.remove(a[0]); yield null; }
        default -> NOPE;
    });

    private final ReportAudience audience = new ReportAudience(null) {
        @Override public List<Colleague> readers(TenantId t) {
            return rights.entrySet().stream().filter(e -> e.getValue().contains("HR_REPORT_READ")).map(e -> new Colleague(e.getKey(), names.get(e.getKey()))).toList();
        }
        @Override public Set<String> held(TenantId t, UUID u) { return rights.getOrDefault(u, Set.of()); }
        @Override public Map<UUID, String> names(TenantId t, Collection<UUID> ids) {
            Map<UUID, String> out = new HashMap<>();
            for (UUID id : ids) out.put(id, names.get(id));
            return out;
        }
    };

    private final SavedReportService service = new SavedReportService(repo, null, shareRepo, audience, (t, u) -> Optional.empty());

    {
        rights.put(ann, PAY); rights.put(ben, READER); rights.put(cleo, PAY); rights.put(dan, Set.of());
        names.put(ann, "Ann Smith"); names.put(ben, "Ben Jones"); names.put(cleo, "Cleo Naidoo"); names.put(dan, "Dan Pillay");
    }

    private View save(UUID owner, String kind) {
        return service.save(tenant, owner, rights.get(owner), new SaveRequest("Monthly", kind, "LAST_MONTH", "NONE", null), TODAY);
    }

    @Test
    void anOwnerCanChangeAReportAndItKeepsItsId() {
        View v = save(ann, "WORKFORCE");
        View changed = service.update(tenant, ann, PAY, v.id(), new SaveRequest(" Quarterly ", "TIME", "LAST_QUARTER", "MONTHLY", 5, true), TODAY);
        assertEquals(v.id(), changed.id());
        assertEquals("Quarterly", changed.name());
        assertEquals("TIME", changed.kind());
        assertEquals("MONTHLY", changed.schedule());
        assertEquals(Integer.valueOf(5), changed.runDay());
        assertTrue(changed.emailReminder());
    }

    @Test
    void emailIsOnlyKeptWhenThereIsAReminder() {
        View v = service.save(tenant, ann, PAY, new SaveRequest("A", "WORKFORCE", "LAST_MONTH", "NONE", null, true), TODAY);
        assertFalse(v.emailReminder());
    }

    @Test
    void someoneElseCannotChangeAReport() {
        View v = save(ann, "WORKFORCE");
        assertThrows(ResourceNotFoundException.class, () -> service.update(tenant, ben, READER, v.id(), new SaveRequest("X", "TIME", "LAST_MONTH", "NONE", null), TODAY));
        assertEquals("Monthly", reports.get(0).getName());
    }

    @Test
    void changingToPayrollNeedsThePayRightAndNoShares() {
        View v = save(ben, "WORKFORCE");
        assertThrows(AccessDeniedException.class, () -> service.update(tenant, ben, READER, v.id(), new SaveRequest("P", "PAYROLL", "LAST_MONTH", "NONE", null), TODAY));
        View w = save(ann, "WORKFORCE");
        service.share(tenant, ann, w.id(), List.of(ben), TODAY);
        assertThrows(IllegalStateException.class, () -> service.update(tenant, ann, PAY, w.id(), new SaveRequest("P", "PAYROLL", "LAST_MONTH", "NONE", null), TODAY));
    }

    @Test
    void aBadChangeIsRefusedWithTheSameMessagesAsSaving() {
        View v = save(ann, "WORKFORCE");
        assertThrows(IllegalArgumentException.class, () -> service.update(tenant, ann, PAY, v.id(), new SaveRequest(" ", "TIME", "LAST_MONTH", "NONE", null), TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.update(tenant, ann, PAY, v.id(), new SaveRequest("N", "TIME", "LAST_MONTH", "WEEKLY", 9), TODAY));
    }

    @Test
    void aSharedReportShowsInTheColleaguesListWithTheOwnersName() {
        View v = save(ann, "WORKFORCE");
        View shared = service.share(tenant, ann, v.id(), List.of(ben), TODAY);
        assertEquals(List.of(ben), shared.sharedWith());
        List<View> forBen = service.mine(tenant, ben, READER, TODAY);
        assertEquals(1, forBen.size());
        assertTrue(forBen.get(0).shared());
        assertEquals("Ann Smith", forBen.get(0).ownerName());
        assertEquals("NONE", forBen.get(0).schedule());
        assertEquals(List.of(ben), service.mine(tenant, ann, PAY, TODAY).get(0).sharedWith());
    }

    @Test
    void sharingReplacesTheListAndIgnoresTheOwner() {
        View v = save(ann, "WORKFORCE");
        service.share(tenant, ann, v.id(), List.of(ben, cleo, ann), TODAY);
        assertEquals(2, shares.size());
        service.share(tenant, ann, v.id(), List.of(cleo), TODAY);
        assertEquals(1, shares.size());
        assertEquals(cleo, shares.get(0).getUserId());
        assertTrue(service.mine(tenant, ben, READER, TODAY).isEmpty());
        service.share(tenant, ann, v.id(), List.of(), TODAY);
        assertTrue(shares.isEmpty());
    }

    @Test
    void youCannotShareWithSomeoneWhoCannotOpenTheReports() {
        View v = save(ann, "WORKFORCE");
        assertThrows(IllegalArgumentException.class, () -> service.share(tenant, ann, v.id(), List.of(dan), TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.share(tenant, ann, v.id(), List.of(UUID.randomUUID()), TODAY));
        assertTrue(shares.isEmpty());
    }

    @Test
    void aPayrollReportOnlyGoesToPeopleWhoMaySeePay() {
        View v = save(ann, "PAYROLL");
        assertThrows(IllegalArgumentException.class, () -> service.share(tenant, ann, v.id(), List.of(ben), TODAY));
        service.share(tenant, ann, v.id(), List.of(cleo), TODAY);
        assertEquals(1, service.mine(tenant, cleo, PAY, TODAY).size());
    }

    @Test
    void aSharedPayrollReportIsHiddenOnceTheColleagueLosesTheRightToSeePay() {
        View v = save(ann, "PAYROLL");
        service.share(tenant, ann, v.id(), List.of(cleo), TODAY);
        assertTrue(service.mine(tenant, cleo, READER, TODAY).isEmpty());
    }

    @Test
    void onlyTheOwnerCanShare() {
        View v = save(ann, "WORKFORCE");
        assertThrows(ResourceNotFoundException.class, () -> service.share(tenant, ben, v.id(), List.of(cleo), TODAY));
    }

    @Test
    void notMoreThanTheLimit() {
        View v = save(ann, "WORKFORCE");
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) { UUID u = UUID.randomUUID(); rights.put(u, READER); names.put(u, "U" + i); many.add(u); }
        assertThrows(IllegalArgumentException.class, () -> service.share(tenant, ann, v.id(), many, TODAY));
    }

    @Test
    void aColleagueCanLeaveButNotTheOwnersOtherShares() {
        View v = save(ann, "WORKFORCE");
        service.share(tenant, ann, v.id(), List.of(ben, cleo), TODAY);
        service.leave(tenant, ben, v.id());
        assertEquals(1, shares.size());
        assertEquals(cleo, shares.get(0).getUserId());
        assertEquals(1, reports.size());
        assertThrows(ResourceNotFoundException.class, () -> service.leave(tenant, ben, v.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.leave(tenant, dan, v.id()));
    }

    @Test
    void theColleagueListHasReadersOnlyAndNotMe() {
        List<UUID> ids = service.colleagues(tenant, ann).stream().map(Colleague::id).toList();
        assertTrue(ids.contains(ben));
        assertTrue(ids.contains(cleo));
        assertFalse(ids.contains(ann));
        assertFalse(ids.contains(dan));
    }
}
