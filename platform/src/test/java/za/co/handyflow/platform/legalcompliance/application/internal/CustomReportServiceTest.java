package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.application.internal.CustomReportRules.*;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.Audience;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.Format;
import za.co.handyflow.platform.legalcompliance.domain.model.CustomReport;
import za.co.handyflow.platform.legalcompliance.domain.repository.CustomReportRepository;
import za.co.handyflow.platform.legalcompliance.dto.CustomReportDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CustomReportServiceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final UUID ANNA = UUID.randomUUID(), BEN = UUID.randomUUID();
    private static final List<String> COLS = List.of("Reference", "Title", "Score");

    private final List<CustomReport> store = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private int baseRuns;
    private List<String> baseColumns = COLS;
    private CustomReportService service;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> m.getName().equals("toString") ? type.getSimpleName() : handler.apply(m.getName(), args == null ? new Object[0] : args));
    }

    @BeforeEach
    void setUp() {
        CustomReportRepository repo = proxy(CustomReportRepository.class, (n, a) -> switch (n) {
            case "findForTenant" -> store.stream().filter(c -> c.getId().equals(a[1])).findFirst();
            case "visibleTo" -> store.stream().filter(c -> c.isOwnedBy((UUID) a[1]) || c.isShared()).toList();
            case "countOwned" -> store.stream().filter(c -> c.isOwnedBy((UUID) a[1])).count();
            case "nameTaken" -> store.stream().anyMatch(c -> c.isOwnedBy((UUID) a[1]) && c.getName().equalsIgnoreCase((String) a[2]) && !c.getId().equals(a[3]));
            case "save" -> { if (!store.contains(a[0])) store.add((CustomReport) a[0]); yield a[0]; }
            case "delete" -> { store.remove(a[0]); yield null; }
            default -> null;
        });
        ReportService reports = new ReportService(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) {
            @Override public List<ReportInfo> catalogue(Audience who) {
                List<ReportInfo> l = new ArrayList<>(List.of(new ReportInfo("RISKS", "Risk", "Risk register", "d", false, false)));
                if (who == Audience.ADMIN) l.add(new ReportInfo("AUDIT_TRAIL", "Audit", "Audit trail", "d", true, false));
                return l;
            }
            @Override public ReportResult run(TenantId t, String key, String asOf, String by, Audience who) {
                if (!catalogue(who).stream().anyMatch(i -> i.key().equals(key))) throw new ResourceNotFoundException("Report", key);
                baseRuns++;
                return new ReportResult(key, "Risk register", "Risk", Instant.now(), by, null, baseColumns,
                        List.of(List.of("R-1", "Data breach", "9"), List.of("R-2", "Supplier", "10"), List.of("R-3", "Policy", "2")), 3, false);
            }
        };
        RegulatoryAuditService trail = new RegulatoryAuditService(null) {
            @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID a, String n) { audit.add(ev + "|" + d); }
        };
        service = new CustomReportService(repo, reports, trail);
    }

    private SaveRequest req(String name, boolean shared) {
        return new SaveRequest(name, "d", "risks", List.of("Title", "Score"), List.of(new Filter("Score", Op.GT, "5")), "Score", true, shared);
    }

    private View make(UUID who, String name, boolean shared) { return service.create(T, req(name, shared), who, "User", Audience.STAFF, true); }

    @Test @DisplayName("Create, run: the view is applied to the standard report and the key is normalised")
    void createAndRun() {
        View v = make(ANNA, "Top risks", false);
        assertEquals("RISKS", v.baseKey());
        assertEquals("Risk register", v.baseTitle());
        assertTrue(v.mine() && v.canEdit());
        ReportResult r = service.run(T, v.id(), null, ANNA, "Anna", Audience.STAFF);
        assertEquals("Top risks", r.title());
        assertEquals(List.of("Title", "Score"), r.columns());
        assertEquals(List.of(List.of("Supplier", "10"), List.of("Data breach", "9")), r.rows());
        assertEquals(2, r.rowCount());
        assertTrue(audit.get(0).startsWith("CUSTOM_CREATED|"));
    }

    @Test @DisplayName("Invalid requests are 400s and save nothing: unknown column, bad name, a report that does not exist")
    void validation() {
        SaveRequest bad = new SaveRequest("Top risks", null, "risks", List.of("Nope"), List.of(), null, false, false);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(HandyFlowException.class, () -> service.create(T, bad, ANNA, "Anna", Audience.STAFF, true)).getStatus());
        SaveRequest shortName = new SaveRequest("ab", null, "risks", List.of("Title"), List.of(), null, false, false);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(HandyFlowException.class, () -> service.create(T, shortName, ANNA, "Anna", Audience.STAFF, true)).getStatus());
        SaveRequest nobase = new SaveRequest("Top risks", null, "NOPE", List.of("Title"), List.of(), null, false, false);
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, nobase, ANNA, "Anna", Audience.STAFF, true));
        assertTrue(store.isEmpty());
        assertTrue(audit.isEmpty());
    }

    @Test @DisplayName("A staff member cannot build a view on an admin-only report (it is 'not found'), and an admin can")
    void audienceOnCreate() {
        SaveRequest onAudit = new SaveRequest("Trail view", null, "AUDIT_TRAIL", List.of("Title"), List.of(), null, false, false);
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, onAudit, ANNA, "Anna", Audience.STAFF, true));
        assertEquals("AUDIT_TRAIL", service.create(T, onAudit, ANNA, "Anna", Audience.ADMIN, true).baseKey());
    }

    @Test @DisplayName("A shared view built on an admin-only report is 'not found' to staff when they run it, and shows no base title in the list")
    void audienceOnRun() {
        SaveRequest onAudit = new SaveRequest("Trail view", null, "AUDIT_TRAIL", List.of("Title"), List.of(), null, false, true);
        UUID id = service.create(T, onAudit, ANNA, "Anna", Audience.ADMIN, true).id();
        assertThrows(ResourceNotFoundException.class, () -> service.run(T, id, null, BEN, "Ben", Audience.STAFF));
        assertNull(service.list(T, BEN, Audience.STAFF, false).get(0).baseTitle());
        assertEquals("Audit trail", service.list(T, BEN, Audience.ADMIN, true).get(0).baseTitle());
        assertEquals(1, service.run(T, id, null, BEN, "Ben", Audience.ADMIN).columns().size());
    }

    @Test @DisplayName("Private views are visible only to their owner; shared ones to everyone")
    void visibility() {
        View mine = make(ANNA, "Private one", false), shared = make(ANNA, "Shared one", true);
        assertEquals(List.of("Shared one"), service.list(T, BEN, Audience.STAFF, false).stream().map(View::name).toList());
        assertEquals(2, service.list(T, ANNA, Audience.STAFF, false).size());
        assertThrows(ResourceNotFoundException.class, () -> service.get(T, mine.id(), BEN, Audience.STAFF, false));
        assertThrows(ResourceNotFoundException.class, () -> service.run(T, mine.id(), null, BEN, "Ben", Audience.STAFF));
        View seen = service.get(T, shared.id(), BEN, Audience.STAFF, false);
        assertFalse(seen.mine());
        assertFalse(seen.canEdit());
        assertTrue(service.get(T, shared.id(), BEN, Audience.STAFF, true).canEdit(), "an administrator may edit");
    }

    @Test @DisplayName("Only a manager can share; a non-manager cannot flip a private view to shared")
    void sharing() {
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.create(T, req("Shared one", true), ANNA, "Anna", Audience.STAFF, false));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        View v = service.create(T, req("Private one", false), ANNA, "Anna", Audience.STAFF, false);
        assertThrows(HandyFlowException.class, () -> service.update(T, v.id(), req("Private one", true), ANNA, "Anna", Audience.STAFF, false, false));
        assertFalse(store.get(0).isShared());
        assertTrue(service.update(T, v.id(), req("Private one", true), ANNA, "Anna", Audience.STAFF, true, false).shared());
        assertTrue(audit.stream().anyMatch(a -> a.contains("now shared")));
        assertTrue(service.update(T, v.id(), req("Private one", true), ANNA, "Anna", Audience.STAFF, false, false).shared(), "an owner without manager rights may keep it shared");
    }

    @Test @DisplayName("Only the owner or an administrator can change or delete; a stranger gets 403 on a shared view and 404 on a private one")
    void editing() {
        View shared = make(ANNA, "Shared one", true), priv = make(ANNA, "Private one", false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(HandyFlowException.class, () -> service.update(T, shared.id(), req("Hacked", true), BEN, "Ben", Audience.STAFF, true, false)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(HandyFlowException.class, () -> service.delete(T, shared.id(), BEN, "Ben", false)).getStatus());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(T, priv.id(), BEN, "Ben", true), "even an administrator cannot see someone's private view");
        assertEquals("Renamed", service.update(T, shared.id(), req("Renamed", true), ANNA, "Anna", Audience.STAFF, true, false).name());
        service.delete(T, shared.id(), BEN, "Ben", true);
        assertEquals(1, store.size());
        assertTrue(audit.stream().anyMatch(a -> a.startsWith("CUSTOM_DELETED|")));
    }

    @Test @DisplayName("Names are unique per owner ignoring case, and the same name is fine for someone else; there is a limit per person")
    void uniqueNamesAndLimit() {
        make(ANNA, "Top risks", false);
        assertEquals(HttpStatus.CONFLICT, assertThrows(HandyFlowException.class, () -> make(ANNA, "TOP RISKS", false)).getStatus());
        assertNotNull(make(BEN, "Top risks", false));
        for (int i = 0; i < CustomReportRules.MAX_PER_USER - 1; i++) make(ANNA, "Report " + i, false);
        assertEquals((long) CustomReportRules.MAX_PER_USER, store.stream().filter(c -> c.isOwnedBy(ANNA)).count());
        assertThrows(HandyFlowException.class, () -> make(ANNA, "One too many", false));
        View v = make(BEN, "Mine", false);
        assertThrows(HandyFlowException.class, () -> service.update(T, v.id(), req("top risks", false), BEN, "Ben", Audience.STAFF, true, false));
    }

    @Test @DisplayName("If the standard report's columns change, running says the view needs editing instead of returning wrong data")
    void staleView() {
        View v = make(ANNA, "Top risks", false);
        baseColumns = List.of("Reference", "Title");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.run(T, v.id(), null, ANNA, "Anna", Audience.STAFF));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertTrue(e.getMessage().contains("needs editing"));
    }

    @Test @DisplayName("Export runs the view, writes an audit entry with the name and row count, and names the file after the report")
    void exports() {
        View v = make(ANNA, "Top risks (Q4)!", false);
        audit.clear();
        ReportService.ExportFile f = service.export(T, v.id(), null, ANNA, "Anna", Audience.STAFF, Format.CSV);
        String csv = new String(f.bytes(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Title,Score") && csv.contains("Supplier,10") && !csv.contains("Policy"));
        assertTrue(f.fileName().startsWith("legal-compliance-custom-top-risks-q4-") && f.fileName().endsWith(".csv"), f.fileName());
        assertEquals(1, audit.size());
        assertTrue(audit.get(0).startsWith("EXPORTED|") && audit.get(0).contains("Top risks (Q4)!") && audit.get(0).contains("2 row(s)"));
        assertThrows(ResourceNotFoundException.class, () -> service.export(T, v.id(), null, BEN, "Ben", Audience.STAFF, Format.CSV));
        assertEquals(1, audit.size(), "a refused export writes nothing");
    }

    @Test @DisplayName("slug keeps file names safe")
    void slugs() {
        assertEquals("top-risks-q4", CustomReportService.slug("  Top risks (Q4)! "));
        assertEquals("report", CustomReportService.slug("***"));
        assertEquals(40, CustomReportService.slug("a".repeat(100)).length());
        assertEquals("a-b", CustomReportService.slug("a/../b"));
    }
}
