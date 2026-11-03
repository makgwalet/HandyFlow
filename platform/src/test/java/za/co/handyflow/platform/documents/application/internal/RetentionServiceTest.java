package za.co.handyflow.platform.documents.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.RetentionProvider;
import za.co.handyflow.platform.documents.application.RetentionViews;
import za.co.handyflow.platform.documents.domain.model.DisposalBatch;
import za.co.handyflow.platform.documents.domain.model.DisposalBatchItem;
import za.co.handyflow.platform.documents.domain.model.RetentionItem;
import za.co.handyflow.platform.documents.domain.model.RetentionPolicy;
import za.co.handyflow.platform.documents.domain.repository.DisposalBatchItemRepository;
import za.co.handyflow.platform.documents.domain.repository.DisposalBatchRepository;
import za.co.handyflow.platform.documents.domain.repository.RetentionItemRepository;
import za.co.handyflow.platform.documents.domain.repository.RetentionPolicyRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Records retention against in-memory repositories (java.lang.reflect.Proxy fakes, no Mockito) and a fake module provider, with
 * "today" fixed at 2026-11-03. Covers the clock, policies, legal hold and the two-step, two-person disposal.
 */
class RetentionServiceTest {

    // ── fakes ────────────────────────────────────────────────────────────────

    final Map<UUID, RetentionPolicy> policyRows = new LinkedHashMap<>();
    final Map<UUID, RetentionItem> itemRows = new LinkedHashMap<>();
    final Map<UUID, DisposalBatch> batchRows = new LinkedHashMap<>();
    final Map<DisposalBatchItem.Key, DisposalBatchItem> batchItemRows = new LinkedHashMap<>();
    final List<String> audit = new ArrayList<>();

    /** A module that records what it was asked to do, and can be told to refuse. */
    static class FakeModule implements RetentionProvider {
        final String code;
        final List<UUID> disposed = new ArrayList<>();
        final List<String> holds = new ArrayList<>();
        Predicate<UUID> refuse = id -> false;
        FakeModule(String code) { this.code = code; }
        @Override public String moduleCode() { return code; }
        @Override public Disposed dispose(TenantId t, String type, UUID id, UUID batch, UUID by) {
            if (refuse.test(id)) throw new IllegalStateException("The module refused");
            disposed.add(id);
            return new Disposed(1, "1 file removed; sha256 abc");
        }
        @Override public void legalHoldChanged(TenantId t, String type, UUID id, boolean hold, String reason, UUID by, String byName) {
            holds.add((hold ? "HOLD " : "LIFT ") + id);
        }
    }

    FakeModule projects = new FakeModule("PROJECTS");

    static Object objectMethod(Object proxy, Method m, Object[] a) {
        return switch (m.getName()) {
            case "toString" -> "fake";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == a[0];
            default -> throw new UnsupportedOperationException(m.getName());
        };
    }

    @SuppressWarnings("unchecked")
    static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return (T) Proxy.newProxyInstance(RetentionServiceTest.class.getClassLoader(), new Class<?>[]{type}, h);
    }

    static int size(Object pageable) { return ((Pageable) pageable).getPageSize(); }

    RetentionPolicyRepository policyRepo() {
        return fake(RetentionPolicyRepository.class, (p, m, a) -> switch (m.getName()) {
            case "save" -> { RetentionPolicy r = (RetentionPolicy) a[0]; policyRows.put(r.getId(), r); yield r; }
            case "findForTenant" -> policyRows.values().stream().filter(r -> r.getTenantId().equals(a[0])).collect(Collectors.toList());
            case "findOne" -> policyRows.values().stream().filter(r -> r.getTenantId().equals(a[0]) && r.getModuleCode().equals(a[1])
                    && r.getCategory().equals(a[2]) && r.getJurisdiction().equals(a[3])).findFirst();
            case "findOwned" -> Optional.ofNullable(policyRows.get((UUID) a[1])).filter(r -> r.getTenantId().equals(a[0]));
            case "delete" -> { policyRows.remove(((RetentionPolicy) a[0]).getId()); yield null; }
            case "flush" -> null;
            default -> objectMethod(p, m, a);
        });
    }

    List<RetentionItem> tenantItems(Object t) { return itemRows.values().stream().filter(i -> i.getTenantId().equals(t)).toList(); }

    static final Comparator<RetentionItem> BY_DATE = Comparator.comparing(RetentionItem::getRetainUntil, Comparator.nullsLast(Comparator.naturalOrder()));

    RetentionItemRepository itemRepo() {
        return fake(RetentionItemRepository.class, (p, m, a) -> switch (m.getName()) {
            case "save" -> { RetentionItem i = (RetentionItem) a[0]; itemRows.put(i.getId(), i); yield i; }
            case "findBySource" -> tenantItems(a[0]).stream().filter(i -> i.getModuleCode().equals(a[1]) && i.getSourceType().equals(a[2])
                    && i.getSourceId().equals(a[3])).findFirst();
            case "findBySources" -> tenantItems(a[0]).stream().filter(i -> i.getModuleCode().equals(a[1]) && i.getSourceType().equals(a[2])
                    && ((Collection<?>) a[3]).contains(i.getSourceId())).collect(Collectors.toList());
            case "findOwned", "findOwnedForUpdate" -> Optional.ofNullable(itemRows.get((UUID) a[1])).filter(i -> i.getTenantId().equals(a[0]));
            case "findDue" -> tenantItems(a[0]).stream().filter(i -> i.isDue((LocalDate) a[1])).sorted(BY_DATE).limit(size(a[2])).collect(Collectors.toList());
            case "findUpcoming" -> tenantItems(a[0]).stream().filter(i -> "ACTIVE".equals(i.getStatus()) && !i.isLegalHold() && i.getRetainUntil() != null
                    && !i.getRetainUntil().isBefore((LocalDate) a[1]) && !i.getRetainUntil().isAfter((LocalDate) a[2])).sorted(BY_DATE).limit(size(a[3])).collect(Collectors.toList());
            case "findHeld" -> tenantItems(a[0]).stream().filter(i -> i.isLegalHold() && !i.isDisposed()).limit(size(a[1])).collect(Collectors.toList());
            case "findDisposed" -> tenantItems(a[0]).stream().filter(RetentionItem::isDisposed).limit(size(a[1])).collect(Collectors.toList());
            case "findKept" -> tenantItems(a[0]).stream().filter(i -> !i.isDisposed()).sorted(BY_DATE).limit(size(a[1])).collect(Collectors.toList());
            case "findKeptForModule" -> tenantItems(a[0]).stream().filter(i -> !i.isDisposed() && i.getModuleCode().equals(a[1])).sorted(BY_DATE).limit(size(a[2])).collect(Collectors.toList());
            case "findOwnedIn" -> tenantItems(a[0]).stream().filter(i -> ((Collection<?>) a[1]).contains(i.getId())).collect(Collectors.toList());
            case "findClockedForModule" -> tenantItems(a[0]).stream().filter(i -> i.getModuleCode().equals(a[1]) && !i.isDisposed() && i.getClockStart() != null).collect(Collectors.toList());
            case "countKeptByCategory" -> tenantItems(a[0]).stream().filter(i -> !i.isDisposed())
                    .collect(Collectors.groupingBy(i -> i.getModuleCode() + "|" + i.getCategory() + "|" + i.getJurisdiction(), TreeMap::new, Collectors.counting()))
                    .entrySet().stream().map(e -> { String[] k = e.getKey().split("\\|"); return new Object[]{k[0], k[1], k[2], e.getValue()}; }).collect(Collectors.toList());
            case "countByStatus" -> tenantItems(a[0]).stream().collect(Collectors.groupingBy(RetentionItem::getStatus, Collectors.counting()))
                    .entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).collect(Collectors.toList());
            case "countHeld" -> tenantItems(a[0]).stream().filter(i -> i.isLegalHold() && !i.isDisposed()).count();
            case "countDue" -> tenantItems(a[0]).stream().filter(i -> i.isDue((LocalDate) a[1])).count();
            case "countUpcoming" -> tenantItems(a[0]).stream().filter(i -> "ACTIVE".equals(i.getStatus()) && !i.isLegalHold() && i.getRetainUntil() != null
                    && !i.getRetainUntil().isBefore((LocalDate) a[1]) && !i.getRetainUntil().isAfter((LocalDate) a[2])).count();
            case "countClockNotStarted" -> tenantItems(a[0]).stream().filter(i -> !i.isDisposed() && i.getClockStart() == null).count();
            default -> objectMethod(p, m, a);
        });
    }

    DisposalBatchRepository batchRepo() {
        return fake(DisposalBatchRepository.class, (p, m, a) -> switch (m.getName()) {
            case "save" -> { DisposalBatch b = (DisposalBatch) a[0]; batchRows.put(b.getId(), b); yield b; }
            case "findOwned", "findOwnedForUpdate" -> Optional.ofNullable(batchRows.get((UUID) a[1])).filter(b -> b.getTenantId().equals(a[0]));
            case "findForTenant" -> batchRows.values().stream().filter(b -> b.getTenantId().equals(a[0]))
                    .sorted(Comparator.comparing(DisposalBatch::getProposedAt).reversed()).limit(size(a[1])).collect(Collectors.toList());
            case "countOpen" -> batchRows.values().stream().filter(b -> b.getTenantId().equals(a[0]) && b.isOpen()).count();
            default -> objectMethod(p, m, a);
        });
    }

    DisposalBatchItemRepository batchItemRepo() {
        return fake(DisposalBatchItemRepository.class, (p, m, a) -> switch (m.getName()) {
            case "save" -> { DisposalBatchItem b = (DisposalBatchItem) a[0]; batchItemRows.put(new DisposalBatchItem.Key(b.getBatchId(), b.getItemId()), b); yield b; }
            case "findForBatch" -> batchItemRows.values().stream().filter(b -> b.getBatchId().equals(a[0])).collect(Collectors.toList());
            case "findById" -> Optional.ofNullable(batchItemRows.get((DisposalBatchItem.Key) a[0]));
            default -> objectMethod(p, m, a);
        });
    }

    AuditTrailFacade trail() {
        return fake(AuditTrailFacade.class, (p, m, a) -> {
            if (m.getName().equals("record")) { audit.add((String) a[1]); return null; }
            return objectMethod(p, m, a);
        });
    }

    @SuppressWarnings("unchecked")
    ObjectProvider<RetentionProvider> providers(RetentionProvider... ps) {
        return fake(ObjectProvider.class, (p, m, a) -> switch (m.getName()) {
            case "orderedStream", "stream" -> Arrays.stream(ps);
            default -> objectMethod(p, m, a);
        });
    }

    static final PlatformTransactionManager TX = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(TransactionDefinition d) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus s) {}
        @Override public void rollback(TransactionStatus s) {}
    };

    RetentionService svc;
    TenantId tenant = TenantId.generate();
    TenantId other = TenantId.generate();
    UUID alice = UUID.randomUUID(), bongani = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @BeforeEach
    void setUp() {
        policyRows.clear(); itemRows.clear(); batchRows.clear(); batchItemRows.clear(); audit.clear();
        projects = new FakeModule("PROJECTS");
        svc = new RetentionService(policyRepo(), itemRepo(), batchRepo(), batchItemRepo(), trail(), providers(projects), TX);
        svc.clock = Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC);
    }

    /** A Projects document tracked, with the clock started on {@code closed} (null = project still open). */
    UUID doc(String ref, String category, LocalDate closed) {
        UUID id = UUID.randomUUID();
        svc.track(tenant, "PROJECTS", "DOCUMENT", id, category, ref, "Title " + ref, "Main Road Bridge");
        if (closed != null) svc.startClock(tenant, "PROJECTS", "DOCUMENT", List.of(id), closed);
        return item(id).getId();
    }

    RetentionItem item(UUID sourceId) {
        return itemRows.values().stream().filter(i -> i.getSourceId().equals(sourceId) || i.getId().equals(sourceId)).findFirst().orElseThrow();
    }

    RetentionViews.Batch propose(UUID by, UUID... ids) {
        return svc.propose(tenant, new RetentionViews.ProposeRequest(List.of(ids), "Past the retention period"), by, "Person", false);
    }

    static String code(Runnable r) {
        try { r.run(); } catch (HandyFlowException e) { return e.getErrorCode(); }
        fail("expected a refusal");
        return null;
    }

    // ── clock and policies ───────────────────────────────────────────────────

    @Test
    void aLiveRecordIsNeverDueAndClosingStartsASevenYearClockOnce() {
        UUID id = UUID.randomUUID();
        svc.track(tenant, "projects", "document", id, "drawing", "DRG-001 rev C", "General arrangement", "Main Road Bridge");
        RetentionItem i = item(id);
        assertEquals("PROJECTS", i.getModuleCode());
        assertEquals("DRAWING", i.getCategory());
        assertNull(i.getRetainUntil());
        assertFalse(i.isDue(TODAY));
        assertEquals(1, svc.startClock(tenant, "PROJECTS", "DOCUMENT", List.of(id, UUID.randomUUID()), LocalDate.of(2019, 6, 30)));
        assertEquals(LocalDate.of(2026, 6, 30), i.getRetainUntil());
        assertEquals(Integer.valueOf(7), i.getPolicyYears());
        assertTrue(i.isDue(TODAY));
        assertEquals(0, svc.startClock(tenant, "PROJECTS", "DOCUMENT", List.of(id), LocalDate.of(2025, 1, 1)), "the first closure date stands");
        assertEquals(LocalDate.of(2026, 6, 30), i.getRetainUntil());
        svc.track(tenant, "PROJECTS", "DOCUMENT", id, "DRAWING", "DRG-001 rev C", "General arrangement (as built)", "Main Road Bridge");
        assertEquals("General arrangement (as built)", i.getTitle());
    }

    @Test
    void aPolicyRedatesItsRecordsAndTheModuleDefaultCoversTheRest() {
        UUID drawing = doc("DRG-001", "DRAWING", LocalDate.of(2019, 6, 30));
        UUID contract = doc("CON-001", "CONTRACT", LocalDate.of(2019, 6, 30));
        RetentionViews.Policy p = svc.savePolicy(tenant, new RetentionViews.PolicyRequest("PROJECTS", "DRAWING", null, 10, false, true, "Building records"), alice, "Alice");
        assertEquals("ZA", p.jurisdiction());
        assertTrue(p.confirmed());
        assertEquals(1, p.recordsKept());
        assertEquals(LocalDate.of(2029, 6, 30), item(drawing).getRetainUntil());
        assertEquals(LocalDate.of(2026, 6, 30), item(contract).getRetainUntil(), "other categories keep the default");

        svc.savePolicy(tenant, new RetentionViews.PolicyRequest("PROJECTS", "*", "ZA", 15, false, false, null), alice, "Alice");
        assertEquals(LocalDate.of(2034, 6, 30), item(contract).getRetainUntil());
        assertEquals(LocalDate.of(2029, 6, 30), item(drawing).getRetainUntil(), "its own policy still wins");

        svc.removePolicy(tenant, p.id(), alice, "Alice");
        assertEquals(LocalDate.of(2034, 6, 30), item(drawing).getRetainUntil(), "falls back to the module's '*' policy");
        assertTrue(audit.contains("RETENTION_POLICY_SAVED"));
        assertTrue(audit.contains("RETENTION_POLICY_REMOVED"));
    }

    @Test
    void thePolicyListShowsCategoriesWithNoPolicyAsTheUnconfirmedDefault() {
        doc("DRG-001", "DRAWING", LocalDate.of(2019, 6, 30));
        doc("RPT-001", "REPORT", null);
        svc.savePolicy(tenant, new RetentionViews.PolicyRequest("PROJECTS", "DRAWING", "ZA", 10, false, true, null), alice, "Alice");
        List<RetentionViews.Policy> list = svc.policies(tenant);
        assertEquals(2, list.size());
        RetentionViews.Policy report = list.stream().filter(x -> x.category().equals("REPORT")).findFirst().orElseThrow();
        assertTrue(report.isDefault());
        assertNull(report.id());
        assertEquals(7, report.retainYears());
        assertFalse(report.confirmed());
        assertEquals(1, svc.summary(tenant).categoriesWithoutConfirmedPolicy());
    }

    @Test
    void policyInputIsChecked() {
        assertThrows(IllegalArgumentException.class, () -> svc.savePolicy(tenant, new RetentionViews.PolicyRequest("PROJECTS", "DRAWING", "ZA", 0, false, false, null), alice, "A"));
        assertThrows(IllegalArgumentException.class, () -> svc.savePolicy(tenant, new RetentionViews.PolicyRequest("PROJECTS", "DRAWING", "South Africa", 5, false, false, null), alice, "A"));
        assertThrows(IllegalArgumentException.class, () -> svc.savePolicy(tenant, new RetentionViews.PolicyRequest(" ", "DRAWING", "ZA", 5, false, false, null), alice, "A"));
        assertTrue(policyRows.isEmpty());
    }

    // ── legal hold ───────────────────────────────────────────────────────────

    @Test
    void aHoldKeepsARecordOffTheDueListAndTellsTheModule() {
        UUID id = doc("CON-001", "CONTRACT", LocalDate.of(2018, 1, 1));
        assertEquals(1, svc.items(tenant, "DUE", null, null).size());
        assertThrows(IllegalArgumentException.class, () -> svc.setHold(tenant, id, new RetentionViews.HoldRequest(true, " "), alice, "Alice"));
        RetentionViews.Item held = svc.setHold(tenant, id, new RetentionViews.HoldRequest(true, "Claim by the client, letter of 1 Nov"), alice, "Alice");
        assertTrue(held.legalHold());
        assertFalse(held.due());
        assertEquals("Alice", held.holdByName());
        assertEquals(List.of("HOLD " + item(id).getSourceId()), projects.holds);
        assertTrue(svc.items(tenant, "DUE", null, null).isEmpty());
        assertEquals(1, svc.items(tenant, "HELD", null, null).size());
        assertEquals("NOT_DUE", code(() -> propose(alice, id)));
        svc.setHold(tenant, id, new RetentionViews.HoldRequest(false, "Claim settled"), alice, "Alice");
        assertEquals(1, svc.items(tenant, "DUE", null, null).size());
        assertEquals(List.of("LEGAL_HOLD_PLACED", "LEGAL_HOLD_LIFTED"), audit);
    }

    @Test
    void aHoldFromTheModuleIsMirroredWithoutCallingTheModuleBack() {
        UUID id = doc("CON-001", "CONTRACT", LocalDate.of(2018, 1, 1));
        svc.moduleHoldChanged(tenant, "PROJECTS", "DOCUMENT", item(id).getSourceId(), true, "Placed in the project register", alice, "Alice");
        assertTrue(item(id).isLegalHold());
        assertTrue(projects.holds.isEmpty());
        svc.moduleHoldChanged(tenant, "PROJECTS", "DOCUMENT", UUID.randomUUID(), true, "untracked", alice, "Alice");
    }

    // ── disposal ─────────────────────────────────────────────────────────────

    @Test
    void onlyDueRecordsCanBeProposedAndNothingIsDeletedOnProposal() {
        UUID due = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        UUID notYet = doc("DRG-002", "DRAWING", LocalDate.of(2024, 1, 1));
        UUID open = doc("RPT-001", "REPORT", null);
        assertEquals("NOT_DUE", code(() -> propose(alice, due, notYet)));
        assertEquals("NOT_DUE", code(() -> propose(alice, open)));
        assertEquals("ACTIVE", item(due).getStatus(), "a refused proposal changes nothing");
        assertTrue(batchRows.isEmpty());

        RetentionViews.Batch b = propose(alice, due);
        assertEquals("PROPOSED", b.status());
        assertEquals(1, b.itemCount());
        assertEquals("PROPOSED", item(due).getStatus());
        assertTrue(projects.disposed.isEmpty(), "proposing deletes nothing");
        assertEquals("NOT_DUE", code(() -> propose(bongani, due)), "already in a request");
        assertThrows(IllegalArgumentException.class, () -> svc.propose(tenant, new RetentionViews.ProposeRequest(List.of(notYet), "x"), alice, "A", false));
    }

    @Test
    void theProposerCanNeverApproveAndTheAttemptIsRecorded() {
        UUID due = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        RetentionViews.Batch b = propose(alice, due);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc.approve(tenant, b.id(), null, alice, "Alice", false));
        assertEquals("SAME_PERSON", e.getErrorCode());
        assertEquals("FORBIDDEN", e.getStatus().name());
        assertTrue(audit.contains("DISPOSAL_REFUSED"));
        assertEquals("SAME_PERSON", code(() -> svc.approve(tenant, b.id(), null, null, "nobody", false)), "an unknown person never approves");
        assertTrue(projects.disposed.isEmpty());
        assertEquals("PROPOSED", batchRows.get(b.id()).getStatus());
    }

    @Test
    void aSecondPersonApprovesAndTheFilesAreDisposedLeavingTheRecord() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        UUID c = doc("DRG-002", "DRAWING", LocalDate.of(2018, 1, 1));
        RetentionViews.Batch b = propose(alice, a, c);
        RetentionViews.Batch done = svc.approve(tenant, b.id(), new RetentionViews.DecisionRequest("Checked against the register"), bongani, "Bongani", false);
        assertEquals("COMPLETED", done.status());
        assertEquals(2, done.disposedCount());
        assertEquals(0, done.failedCount());
        assertEquals("Bongani", done.decidedByName());
        assertEquals(2, projects.disposed.size());
        RetentionItem i = item(a);
        assertEquals("DISPOSED", i.getStatus());
        assertEquals(b.id(), i.getBatchId());
        assertNotNull(i.getDisposedAt());
        assertEquals("1 file removed; sha256 abc", i.getDisposalDetail());
        assertEquals(2, done.items().size());
        assertTrue(done.items().stream().allMatch(x -> x.outcome().equals("DISPOSED")));
        assertEquals(2, svc.items(tenant, "DISPOSED", null, null).size());
        assertTrue(audit.containsAll(List.of("DISPOSAL_PROPOSED", "DISPOSAL_APPROVED", "RECORD_DISPOSED", "DISPOSAL_COMPLETED")));
        assertEquals("WRONG_STATE", code(() -> svc.approve(tenant, b.id(), null, bongani, "Bongani", false)), "a finished request cannot run again");
    }

    @Test
    void aDisposedRecordIsFinal() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        svc.approve(tenant, propose(alice, a).id(), null, bongani, "Bongani", false);
        assertEquals("DISPOSED", code(() -> svc.setHold(tenant, a, new RetentionViews.HoldRequest(true, "Too late"), alice, "Alice")));
        svc.track(tenant, "PROJECTS", "DOCUMENT", item(a).getSourceId(), "DRAWING", "changed", "changed", null);
        assertEquals("Title DRG-001", item(a).getTitle(), "tracking a disposed record changes nothing");
        assertEquals(0, svc.startClock(tenant, "PROJECTS", "DOCUMENT", List.of(item(a).getSourceId()), TODAY));
    }

    @Test
    void aHoldPlacedAfterTheProposalSkipsThatRecordOnly() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        UUID c = doc("CON-001", "CONTRACT", LocalDate.of(2018, 1, 1));
        RetentionViews.Batch b = propose(alice, a, c);
        svc.setHold(tenant, c, new RetentionViews.HoldRequest(true, "Dispute opened"), bongani, "Bongani");
        RetentionViews.Batch done = svc.approve(tenant, b.id(), null, bongani, "Bongani", false);
        assertEquals(1, done.disposedCount());
        assertEquals(1, done.skippedCount());
        assertEquals("ACTIVE", item(c).getStatus());
        assertNull(item(c).getBatchId());
        assertTrue(item(c).isLegalHold());
        assertEquals(List.of(item(a).getSourceId()), projects.disposed);
        String why = done.items().stream().filter(x -> x.itemId().equals(c)).findFirst().orElseThrow().detail();
        assertTrue(why.contains("legal hold"));
    }

    @Test
    void aRecordWhosePeriodWasLengthenedAfterTheProposalIsSkipped() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        RetentionViews.Batch b = propose(alice, a);
        svc.savePolicy(tenant, new RetentionViews.PolicyRequest("PROJECTS", "DRAWING", "ZA", 20, false, true, null), bongani, "Bongani");
        RetentionViews.Batch done = svc.approve(tenant, b.id(), null, bongani, "Bongani", false);
        assertEquals(1, done.skippedCount());
        assertEquals("ACTIVE", item(a).getStatus());
        assertEquals(LocalDate.of(2038, 1, 1), item(a).getRetainUntil());
        assertTrue(projects.disposed.isEmpty());
    }

    @Test
    void aModuleRefusalFailsThatRecordAndPutsItBackOnTheList() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        UUID c = doc("DRG-002", "DRAWING", LocalDate.of(2018, 1, 1));
        UUID refused = item(c).getSourceId();
        projects.refuse = refused::equals;
        RetentionViews.Batch done = svc.approve(tenant, propose(alice, a, c).id(), null, bongani, "Bongani", false);
        assertEquals("COMPLETED", done.status());
        assertEquals(1, done.disposedCount());
        assertEquals(1, done.failedCount());
        assertEquals("ACTIVE", item(c).getStatus());
        assertTrue(item(c).isDue(TODAY), "back on the due list to be proposed again");
        assertEquals("The module refused", done.items().stream().filter(x -> x.itemId().equals(c)).findFirst().orElseThrow().detail());
        assertTrue(audit.contains("DISPOSAL_FAILED"));
    }

    @Test
    void aRecordOfAModuleWithNoProviderIsNotDisposed() {
        UUID id = UUID.randomUUID();
        svc.track(tenant, "FLEET", "VEHICLE_FILE", id, "LICENCE", "ABC 123 GP", "Licence", null);
        svc.startClock(tenant, "FLEET", "VEHICLE_FILE", List.of(id), LocalDate.of(2010, 1, 1));
        RetentionViews.Batch done = svc.approve(tenant, propose(alice, item(id).getId()).id(), null, bongani, "Bongani", false);
        assertEquals(1, done.failedCount());
        assertEquals("ACTIVE", item(id).getStatus());
    }

    @Test
    void rejectingNeedsAnotherPersonAndAReasonAndPutsEverythingBack() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        RetentionViews.Batch b = propose(alice, a);
        assertEquals("SAME_PERSON", code(() -> svc.reject(tenant, b.id(), new RetentionViews.DecisionRequest("changed my mind"), alice, "Alice", false)));
        assertThrows(IllegalArgumentException.class, () -> svc.reject(tenant, b.id(), new RetentionViews.DecisionRequest(""), bongani, "Bongani", false));
        RetentionViews.Batch r = svc.reject(tenant, b.id(), new RetentionViews.DecisionRequest("Still needed for the defects period"), bongani, "Bongani", false);
        assertEquals("REJECTED", r.status());
        assertEquals("Still needed for the defects period", r.decisionNote());
        assertEquals("ACTIVE", item(a).getStatus());
        assertEquals("SKIPPED", r.items().get(0).outcome());
        assertEquals("WRONG_STATE", code(() -> svc.approve(tenant, b.id(), null, bongani, "Bongani", false)));
        assertTrue(projects.disposed.isEmpty());
    }

    @Test
    void onlyTheProposerCanWithdraw() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        RetentionViews.Batch b = propose(alice, a);
        assertEquals("NOT_PROPOSER", code(() -> svc.cancel(tenant, b.id(), bongani, "Bongani")));
        assertEquals("CANCELLED", svc.cancel(tenant, b.id(), alice, "Alice").status());
        assertEquals("ACTIVE", item(a).getStatus());
        assertEquals(1, svc.items(tenant, "DUE", null, null).size());
    }

    @Test
    void aSupportSessionCanLookButNotDispose() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        assertEquals("SUPPORT_SESSION", code(() -> svc.propose(tenant, new RetentionViews.ProposeRequest(List.of(a), "Past period"), alice, "Support", true)));
        RetentionViews.Batch b = propose(alice, a);
        assertEquals("SUPPORT_SESSION", code(() -> svc.approve(tenant, b.id(), null, bongani, "Support", true)));
        assertEquals("SUPPORT_SESSION", code(() -> svc.reject(tenant, b.id(), new RetentionViews.DecisionRequest("no no"), bongani, "Support", true)));
        assertEquals(3, audit.stream().filter("DISPOSAL_REFUSED"::equals).count(), "every support attempt is recorded");
        assertTrue(projects.disposed.isEmpty());
    }

    @Test
    void anotherTenantCannotSeeOrTouchAnything() {
        UUID a = doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));
        RetentionViews.Batch b = propose(alice, a);
        assertThrows(ResourceNotFoundException.class, () -> svc.item(other, a));
        assertThrows(ResourceNotFoundException.class, () -> svc.setHold(other, a, new RetentionViews.HoldRequest(true, "Not mine"), bongani, "B"));
        assertThrows(ResourceNotFoundException.class, () -> svc.batch(other, b.id()));
        assertThrows(ResourceNotFoundException.class, () -> svc.approve(other, b.id(), null, bongani, "B", false));
        assertThrows(ResourceNotFoundException.class, () -> svc.propose(other, new RetentionViews.ProposeRequest(List.of(a), "Past period"), bongani, "B", false));
        assertTrue(svc.items(other, "ALL", null, null).isEmpty());
        assertTrue(svc.batches(other, null).isEmpty());
        assertEquals(0, svc.summary(other).tracked());
    }

    @Test
    void theSummaryCountsEachList() {
        doc("DRG-001", "DRAWING", LocalDate.of(2018, 1, 1));                 // due
        UUID held = doc("CON-001", "CONTRACT", LocalDate.of(2018, 1, 1));    // held
        doc("DRG-002", "DRAWING", LocalDate.of(2019, 12, 31));               // 2026-12-31: within 90 days
        doc("RPT-001", "REPORT", null);                                      // clock not started
        svc.setHold(tenant, held, new RetentionViews.HoldRequest(true, "Claim"), alice, "Alice");
        RetentionViews.Summary s = svc.summary(tenant);
        assertEquals(4, s.tracked());
        assertEquals(1, s.due());
        assertEquals(1, s.held());
        assertEquals(1, s.upcoming());
        assertEquals(1, s.clockNotStarted());
        assertEquals(0, s.openRequests());
        assertEquals(1, svc.items(tenant, "UPCOMING", null, null).size());
        assertEquals(4, svc.items(tenant, "ALL", "projects", null).size());
        assertTrue(svc.items(tenant, "DUE", "DOCUMENTS", null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> svc.items(tenant, "EVERYTHING", null, null));
    }

    @Test
    void aProposalIsLimitedTo500Records() {
        List<UUID> many = new ArrayList<>();
        for (int k = 0; k < 501; k++) many.add(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> svc.propose(tenant, new RetentionViews.ProposeRequest(many, "Past period"), alice, "A", false));
    }
}
