package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Client due diligence against in-memory stand-ins for the repositories (no database, no Spring): the preparer / approver split, senior approval,
 * review cycles, the enforcement gate and its setting, ethical walls on the overview. The migration's constraints and triggers were run against PostgreSQL separately.
 */
class LpKycServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID preparer = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();
    private final UUID senior = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    private final Map<UUID, LpKycProfile> profiles = new LinkedHashMap<>();
    private final Map<UUID, LpKycParty> parties = new LinkedHashMap<>();
    private final Map<UUID, LpKycItem> items = new LinkedHashMap<>();
    private final List<LpKycSettingChange> settingChanges = new ArrayList<>();
    private final List<LpTrustSettingChange> trustChanges = new ArrayList<>();
    private final Map<UUID, LpClient> clients = new LinkedHashMap<>();
    private final List<UUID> walledClients = new ArrayList<>();

    private LpKycService service;
    private LpClient person, company;

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler h = (proxy, m, args) -> {
            Function<Object[], Object> f = handlers.get(m.getName());
            if (f != null) return f.apply(args == null ? new Object[0] : args);
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            throw new UnsupportedOperationException(m.getName());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }

    @BeforeEach
    void setUp() {
        profiles.clear(); parties.clear(); items.clear(); settingChanges.clear(); trustChanges.clear(); clients.clear(); walledClients.clear();
        var profileRepo = fake(LpKycProfileRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(profiles.get((UUID) a[1])).filter(p -> p.getTenantId().equals(a[0])),
                "findForClient", a -> newestFirst((TenantId) a[0], c -> c.equals(a[1])),
                "findForClients", a -> newestFirst((TenantId) a[0], c -> ((Collection<?>) a[1]).contains(c)),
                "save", a -> { LpKycProfile p = (LpKycProfile) a[0]; profiles.put(p.getId(), p); return p; }));
        var partyRepo = fake(LpKycPartyRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(parties.get((UUID) a[1])).filter(p -> p.getTenantId().equals(a[0])),
                "findForProfile", a -> parties.values().stream().filter(p -> p.getTenantId().equals(a[0]) && p.getProfileId().equals(a[1])).toList(),
                "save", a -> { LpKycParty p = (LpKycParty) a[0]; parties.put(p.getId(), p); return p; },
                "delete", a -> { parties.remove(((LpKycParty) a[0]).getId()); return null; }));
        var itemRepo = fake(LpKycItemRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(items.get((UUID) a[1])).filter(p -> p.getTenantId().equals(a[0])),
                "findForProfile", a -> items.values().stream().filter(p -> p.getTenantId().equals(a[0]) && p.getProfileId().equals(a[1])).toList(),
                "save", a -> { LpKycItem p = (LpKycItem) a[0]; items.put(p.getId(), p); return p; },
                "delete", a -> { items.remove(((LpKycItem) a[0]).getId()); return null; }));
        var settingRepo = fake(LpKycSettingChangeRepository.class, Map.of(
                "latest", a -> { List<LpKycSettingChange> l = new ArrayList<>(settingChanges); Collections.reverse(l); return l.stream().filter(c -> c.getTenantId().equals(a[0])).toList(); },
                "save", a -> { settingChanges.add((LpKycSettingChange) a[0]); return a[0]; }));
        var clientRepo = fake(LpClientRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(clients.get((UUID) a[1])).filter(c -> c.getTenantId().equals(a[0])),
                "findAllActiveExcluding", a -> new PageImpl<>(clients.values().stream().filter(c -> c.getTenantId().equals(a[0]) && !((List<?>) a[1]).contains(c.getId())).toList())));
        var trustRepo = fake(LpTrustSettingChangeRepository.class, Map.of(
                "latest", a -> { List<LpTrustSettingChange> l = new ArrayList<>(trustChanges); Collections.reverse(l); return l; }));
        LpWallGuard guard = new LpWallGuard() {
            @Override public void requireAccess(TenantId t, LpMatter m) { }
            @Override public void requireMatterAccess(TenantId t, UUID id) { }
            @Override public void requireClientAccess(TenantId t, UUID id) { if (walledClients.contains(id)) throw new AccessDeniedException("walled"); }
            @Override public void requireScopeAccess(TenantId t, UUID m, UUID c) { }
            @Override public Barred barred(TenantId t) { return Barred.of(List.of(), walledClients); }
        };
        service = new LpKycService(profileRepo, partyRepo, itemRepo, settingRepo, clientRepo, new LpTrustSettingsService(trustRepo), guard);
        person = LpClient.create(firm, "Thandi Mokoena", null, null, "INDIVIDUAL", null, null);
        company = LpClient.create(firm, "Dlamini Trading", null, null, "ENTITY", null, null);
        clients.put(person.getId(), person); clients.put(company.getId(), company);
    }

    private List<LpKycProfile> newestFirst(TenantId t, java.util.function.Predicate<UUID> clientMatch) {
        List<LpKycProfile> l = new ArrayList<>(profiles.values().stream().filter(p -> p.getTenantId().equals(t) && clientMatch.test(p.getClientId())).toList());
        Collections.reverse(l);
        return l;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private KycAssessmentRequest low() {
        return new KycAssessmentRequest("LOW", "LOW", "LOW", "LOW", "NONE", today, null, today, "CLEAR", "Residential conveyancing for a salaried client", null, null);
    }

    private KycAssessmentRequest high() {
        return new KycAssessmentRequest("HIGH", "LOW", "LOW", "LOW", "NONE", today, null, today, "CLEAR", "Cross-border property purchase", "Sale of a business in 2024", "Salary and dividends over 20 years");
    }

    private KycAssessmentRequest politicallyExposed() {
        return new KycAssessmentRequest("LOW", "LOW", "LOW", "LOW", "DPEP", today, "Serves on a provincial board", today, "CLEAR", "Property purchase for a board member", "Board fees and salary", "Salary over 15 years");
    }

    private KycItemRequest item(String kind) { return new KycItemRequest(kind, null, "Checked against the original", null, null, today); }

    private UUID startPerson() { return service.start(firm, new StartKycRequest(person.getId(), "INDIVIDUAL"), preparer, "Preparer").id(); }

    private UUID readyToSubmit(KycAssessmentRequest a) {
        UUID id = startPerson();
        service.assess(firm, id, a);
        service.addItem(firm, id, item("IDENTITY"), preparer, "Preparer");
        service.addItem(firm, id, item("ADDRESS"), preparer, "Preparer");
        return id;
    }

    private UUID submitted(KycAssessmentRequest a) {
        UUID id = readyToSubmit(a);
        service.submit(firm, id, preparer, "Preparer");
        return id;
    }

    private UUID verifiedPerson() {
        UUID id = submitted(low());
        service.approve(firm, id, null, false, approver, "Approver");
        return id;
    }

    private void setField(Object o, String name, Object v) {
        try { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, v); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private void allowSelfApproval() { trustChanges.add(LpTrustSettingChange.record(firm, true, "Sole practitioner with no second person", approver, "Admin")); }

    // ---- starting ------------------------------------------------------------------------------------------------------

    @Test
    void startingCreatesADraftForTheClient() {
        var p = service.start(firm, new StartKycRequest(person.getId(), "INDIVIDUAL"), preparer, "Preparer");
        assertEquals("DRAFT", p.status());
        assertEquals("Thandi Mokoena", p.clientName());
        assertFalse(p.missing().isEmpty());
    }

    @Test
    void onlyOneDueDiligenceMayBeOpenPerClient() {
        startPerson();
        var e = assertThrows(IllegalStateException.class, this::startPerson);
        assertTrue(e.getMessage().contains("already"));
    }

    @Test
    void anUnknownClientOrAnotherFirmsClientIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.start(firm, new StartKycRequest(UUID.randomUUID(), "INDIVIDUAL"), preparer, "P"));
        assertThrows(ResourceNotFoundException.class, () -> service.start(otherFirm, new StartKycRequest(person.getId(), "INDIVIDUAL"), preparer, "P"));
    }

    @Test
    void anIndividualClientIsVerifiedAsAnIndividualAndAnEntityMustSayWhatKind() {
        assertThrows(IllegalArgumentException.class, () -> service.start(firm, new StartKycRequest(person.getId(), "COMPANY"), preparer, "P"));
        assertThrows(IllegalArgumentException.class, () -> service.start(firm, new StartKycRequest(company.getId(), "INDIVIDUAL"), preparer, "P"));
        assertEquals("TRUST", service.start(firm, new StartKycRequest(company.getId(), "TRUST"), preparer, "P").subjectKind());
    }

    @Test
    void anotherFirmCannotReadOrChangeAProfile() {
        UUID id = startPerson();
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherFirm, id));
        assertThrows(ResourceNotFoundException.class, () -> service.assess(otherFirm, id, low()));
    }

    // ---- submit and approve --------------------------------------------------------------------------------------------

    @Test
    void aCompleteProfileIsSubmittedAndAnotherPersonVerifiesIt() {
        UUID id = submitted(low());
        var p = service.approve(firm, id, null, false, approver, "Approver");
        assertEquals("VERIFIED", p.status());
        assertEquals(today.plusMonths(36), p.validUntil());
        assertFalse(p.selfApproved());
    }

    @Test
    void anIncompleteProfileCannotBeSubmitted() {
        UUID id = startPerson();
        service.assess(firm, id, low());
        var e = assertThrows(IllegalStateException.class, () -> service.submit(firm, id, preparer, "Preparer"));
        assertTrue(e.getMessage().startsWith("Not complete yet"));
    }

    @Test
    void thePreparerCannotApproveTheirOwnWorkByDefault() {
        UUID id = submitted(low());
        var e = assertThrows(IllegalStateException.class, () -> service.approve(firm, id, "I checked it myself, all fine", false, preparer, "Preparer"));
        assertTrue(e.getMessage().contains("Another person"));
        assertEquals("SUBMITTED", service.get(firm, id).status());
    }

    @Test
    void aFirmThatAllowsItMayApproveItsOwnOrdinaryClientWithAWrittenReason() {
        allowSelfApproval();
        UUID id = submitted(low());
        assertThrows(IllegalArgumentException.class, () -> service.approve(firm, id, "ok", false, preparer, "Preparer"));
        var p = service.approve(firm, id, "Sole practitioner, identity checked in person", false, preparer, "Preparer");
        assertTrue(p.selfApproved());
        assertEquals("VERIFIED", p.status());
    }

    @Test
    void selfApprovalIsNeverAllowedForAHighRiskClientEvenWhenTheFirmAllowsIt() {
        allowSelfApproval();
        UUID id = submitted(high());
        var e = assertThrows(IllegalStateException.class, () -> service.approve(firm, id, "Sole practitioner, identity checked in person", true, preparer, "Preparer"));
        assertTrue(e.getMessage().contains("cannot be approved by the person who submitted"));
    }

    @Test
    void aHighRiskClientNeedsSeniorManagementAndIsReviewedYearly() {
        UUID id = submitted(high());
        assertTrue(service.get(firm, id).needsSenior());
        var e = assertThrows(IllegalStateException.class, () -> service.approve(firm, id, null, false, approver, "Approver"));
        assertTrue(e.getMessage().contains("senior management"));
        var p = service.approve(firm, id, null, true, senior, "Senior");
        assertEquals(today.plusMonths(12), p.validUntil());
        assertTrue(p.seniorApproved());
        assertEquals("ENHANCED", p.dueDiligenceLevel());
    }

    @Test
    void aPoliticallyExposedClientIsTreatedAsHighRisk() {
        UUID id = submitted(politicallyExposed());
        assertEquals("HIGH", service.get(firm, id).riskRating());
        assertThrows(IllegalStateException.class, () -> service.approve(firm, id, null, false, approver, "Approver"));
        assertEquals("VERIFIED", service.approve(firm, id, null, true, senior, "Senior").status());
    }

    @Test
    void approvalChecksCompletenessAgainBeforeVerifying() {
        UUID id = submitted(low());
        var item = items.values().stream().filter(i -> "ADDRESS".equals(i.getKind())).findFirst().orElseThrow();
        items.remove(item.getId());   // somehow gone between submit and approve
        var e = assertThrows(IllegalStateException.class, () -> service.approve(firm, id, null, false, approver, "Approver"));
        assertTrue(e.getMessage().startsWith("No longer complete"));
    }

    @Test
    void returningNeedsAReasonAndReopensTheDraft() {
        UUID id = submitted(low());
        assertThrows(IllegalArgumentException.class, () -> service.returnToDraft(firm, id, "no"));
        var p = service.returnToDraft(firm, id, "Address proof is older than three months");
        assertEquals("DRAFT", p.status());
        assertEquals("Address proof is older than three months", p.returnReason());
        service.assess(firm, id, low());   // editable again
    }

    @Test
    void aSubmittedOrVerifiedProfileCannotBeChanged() {
        UUID id = submitted(low());
        assertThrows(IllegalStateException.class, () -> service.assess(firm, id, low()));
        assertThrows(IllegalStateException.class, () -> service.addItem(firm, id, item("OTHER"), preparer, "P"));
        service.approve(firm, id, null, false, approver, "Approver");
        assertThrows(IllegalStateException.class, () -> service.assess(firm, id, low()));
        assertThrows(IllegalStateException.class, () -> service.addParty(firm, id, new KycPartyRequest("PARTNER", "X", null, null, null, null, today, null), preparer, "P"));
    }

    @Test
    void checkDatesCannotBeInTheFuture() {
        UUID id = startPerson();
        var future = new KycAssessmentRequest("LOW", "LOW", "LOW", "LOW", "NONE", today.plusDays(1), null, today, "CLEAR", "Residential conveyancing for a client", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.assess(firm, id, future));
        assertThrows(IllegalArgumentException.class, () -> service.addItem(firm, id, new KycItemRequest("IDENTITY", null, "Original seen", null, null, today.plusDays(1)), preparer, "P"));
    }

    // ---- people and documents ------------------------------------------------------------------------------------------

    @Test
    void aPartyIsAddedVerifiedAndRemoved() {
        UUID id = service.start(firm, new StartKycRequest(company.getId(), "PARTNERSHIP"), preparer, "P").id();
        var p = service.addParty(firm, id, new KycPartyRequest("PARTNER", "Partner One", "8001015009087", null, null, null, null, null), preparer, "P");
        UUID partyId = p.parties().get(0).id();
        assertNull(p.parties().get(0).verifiedOn());
        assertTrue(p.missing().stream().anyMatch(s -> s.contains("Verify the identity of Partner One")));
        p = service.verifyParty(firm, id, partyId, today, preparer, "P");
        assertEquals(today, p.parties().get(0).verifiedOn());
        p = service.removeParty(firm, id, partyId);
        assertTrue(p.parties().isEmpty());
    }

    @Test
    void aPersonWithDocumentsRecordedAgainstThemCannotBeRemovedUntilThoseAreGone() {
        UUID id = service.start(firm, new StartKycRequest(company.getId(), "PARTNERSHIP"), preparer, "P").id();
        var p = service.addParty(firm, id, new KycPartyRequest("PARTNER", "Partner One", null, null, null, null, today, null), preparer, "P");
        UUID partyId = p.parties().get(0).id();
        p = service.addItem(firm, id, new KycItemRequest("IDENTITY", partyId, "ID book seen", null, null, today), preparer, "P");
        UUID itemId = p.items().get(0).id();
        assertThrows(IllegalStateException.class, () -> service.removeParty(firm, id, partyId));
        service.removeItem(firm, id, itemId);
        service.removeParty(firm, id, partyId);
    }

    @Test
    void aPartyOrItemOfAnotherProfileIsNotFound() {
        UUID mine = startPerson();
        UUID theirs = service.start(firm, new StartKycRequest(company.getId(), "PARTNERSHIP"), preparer, "P").id();
        var withParty = service.addParty(firm, theirs, new KycPartyRequest("PARTNER", "Partner One", null, null, null, null, today, null), preparer, "P");
        UUID partyId = withParty.parties().get(0).id();
        assertThrows(ResourceNotFoundException.class, () -> service.verifyParty(firm, mine, partyId, today, preparer, "P"));
        assertThrows(ResourceNotFoundException.class, () -> service.removeParty(firm, mine, partyId));
        assertThrows(ResourceNotFoundException.class, () -> service.addItem(firm, mine, new KycItemRequest("IDENTITY", partyId, "x", null, null, today), preparer, "P"));
    }

    // ---- review --------------------------------------------------------------------------------------------------------

    @Test
    void aReviewCannotBeOpenedBeforeAClientHasBeenVerified() {
        assertThrows(IllegalStateException.class, () -> service.openReview(firm, person.getId(), preparer, "P"));
        startPerson();
        assertThrows(IllegalStateException.class, () -> service.openReview(firm, person.getId(), preparer, "P"));
    }

    @Test
    void aReviewStartsFromTheLastCycleWithEveryCheckToBeDoneAgain() {
        UUID id = service.start(firm, new StartKycRequest(company.getId(), "PARTNERSHIP"), preparer, "P").id();
        service.assess(firm, id, low());
        service.addParty(firm, id, new KycPartyRequest("PARTNER", "Partner One", null, null, null, null, today, null), preparer, "P");
        service.addParty(firm, id, new KycPartyRequest("PARTNER", "Partner Two", null, null, null, null, today, null), preparer, "P");
        service.addParty(firm, id, new KycPartyRequest("AUTHORISED_PERSON", "Agent", null, null, null, null, today, null), preparer, "P");
        service.addItem(firm, id, item("ADDRESS"), preparer, "P");
        service.submit(firm, id, preparer, "P");
        service.approve(firm, id, null, false, approver, "Approver");

        var review = service.openReview(firm, company.getId(), preparer, "P");
        assertNotEquals(id, review.id());
        assertEquals("DRAFT", review.status());
        assertEquals("LOW", review.riskRating());
        assertEquals(3, review.parties().size());
        assertTrue(review.parties().stream().allMatch(x -> x.verifiedOn() == null));
        assertTrue(review.items().isEmpty());
        assertNull(review.sanctionsCheckedOn());
        assertEquals("VERIFIED", service.get(firm, id).status());   // the old cycle is untouched
        assertThrows(IllegalStateException.class, () -> service.openReview(firm, company.getId(), preparer, "P"));   // one open at a time
    }

    @Test
    void startingAgainOnACurrentlyVerifiedClientIsRefusedButAfterTheReviewDateItOpensAReview() {
        UUID id = verifiedPerson();
        assertThrows(IllegalStateException.class, this::startPerson);
        setField(profiles.get(id), "validUntil", today.minusDays(1));
        var next = startPerson();
        assertNotEquals(id, next);
        assertEquals("DRAFT", service.get(firm, next).status());
    }

    // ---- the gate ------------------------------------------------------------------------------------------------------

    @Test
    void theGateIsOffUntilTheFirmSwitchesItOn() {
        assertFalse(service.enforced(firm));
        service.requireCleared(firm, person.getId(), "open a matter");   // not verified, still allowed
    }

    @Test
    void whenOnAClientWithoutVerifiedDueDiligenceIsRefusedInPlainWords() {
        service.updateSettings(firm, true, "The board resolved to enforce from 1 December", approver, "Admin");
        var e = assertThrows(IllegalStateException.class, () -> service.requireCleared(firm, person.getId(), "open a matter"));
        assertTrue(e.getMessage().contains("cannot open a matter"));
        startPerson();   // a draft does not count
        assertThrows(IllegalStateException.class, () -> service.requireCleared(firm, person.getId(), "open a matter"));
    }

    @Test
    void whenOnAVerifiedClientPassesAndALapsedOneDoesNot() {
        UUID id = verifiedPerson();
        service.updateSettings(firm, true, "The board resolved to enforce from 1 December", approver, "Admin");
        service.requireCleared(firm, person.getId(), "receive money into trust");
        setField(profiles.get(id), "validUntil", today.minusDays(1));
        assertThrows(IllegalStateException.class, () -> service.requireCleared(firm, person.getId(), "receive money into trust"));
    }

    @Test
    void aVerificationStaysValidWhileAReviewIsInProgress() {
        verifiedPerson();
        service.updateSettings(firm, true, "The board resolved to enforce from 1 December", approver, "Admin");
        service.openReview(firm, person.getId(), preparer, "P");
        service.requireCleared(firm, person.getId(), "open a matter");
    }

    @Test
    void theGateOnlyLooksAtTheFirmsOwnRecords() {
        verifiedPerson();
        service.updateSettings(firm, true, "The board resolved to enforce from 1 December", approver, "Admin");
        assertFalse(service.enforced(otherFirm));   // the setting is the firm's own
        service.updateSettings(otherFirm, true, "The other firm enforces too, from today", approver, "Admin");
        assertThrows(IllegalStateException.class, () -> service.requireCleared(otherFirm, person.getId(), "open a matter"));   // our verification does not count for them
    }

    @Test
    void theSettingNeedsAReasonIsKeptInHistoryAndRefusesNoChange() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(firm, true, "short", approver, "Admin"));
        assertThrows(IllegalStateException.class, () -> service.updateSettings(firm, false, "Already off, nothing to change", approver, "Admin"));
        service.updateSettings(firm, true, "The board resolved to enforce from 1 December", approver, "Admin");
        var s = service.updateSettings(firm, false, "Suspended while the intake system is migrated", approver, "Admin");
        assertFalse(s.enforce());
        assertEquals(2, s.history().size());
        assertEquals("Suspended while the intake system is migrated", s.history().get(0).reason());
        assertEquals(2, settingChanges.size());
    }

    // ---- overview and walls ------------------------------------------------------------------------------------------

    @Test
    void theOverviewShowsEachClientsStanding() {
        var rows = service.overview(firm, PageRequest.of(0, 20)).getContent();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> "NOT_STARTED".equals(r.status())));

        verifiedPerson();
        service.start(firm, new StartKycRequest(company.getId(), "COMPANY"), preparer, "P");
        rows = service.overview(firm, PageRequest.of(0, 20)).getContent();
        var personRow = rows.stream().filter(r -> r.clientId().equals(person.getId())).findFirst().orElseThrow();
        var companyRow = rows.stream().filter(r -> r.clientId().equals(company.getId())).findFirst().orElseThrow();
        assertEquals("VERIFIED", personRow.status());
        assertEquals(today.plusMonths(36), personRow.validUntil());
        assertEquals("LOW", personRow.riskRating());
        assertFalse(personRow.reviewOpen());
        assertEquals("DRAFT", companyRow.status());
    }

    @Test
    void theOverviewMarksAReviewOpenAndAnExpiredClient() {
        UUID id = verifiedPerson();
        service.openReview(firm, person.getId(), preparer, "P");
        var row = service.overview(firm, PageRequest.of(0, 20)).getContent().stream().filter(r -> r.clientId().equals(person.getId())).findFirst().orElseThrow();
        assertEquals("VERIFIED", row.status());
        assertTrue(row.reviewOpen());

        setField(profiles.get(id), "validUntil", today.minusDays(1));
        row = service.overview(firm, PageRequest.of(0, 20)).getContent().stream().filter(r -> r.clientId().equals(person.getId())).findFirst().orElseThrow();
        assertEquals("DRAFT", row.status());   // the open review is the latest word

        profiles.values().removeIf(p -> "DRAFT".equals(p.getStatus()));
        row = service.overview(firm, PageRequest.of(0, 20)).getContent().stream().filter(r -> r.clientId().equals(person.getId())).findFirst().orElseThrow();
        assertEquals("EXPIRED", row.status());
    }

    @Test
    void aWalledClientIsLeftOffTheOverviewAndCannotBeReadOrStarted() {
        verifiedPerson();
        walledClients.add(person.getId());
        var rows = service.overview(firm, PageRequest.of(0, 20)).getContent();
        assertEquals(1, rows.size());
        assertEquals(company.getId(), rows.get(0).clientId());
        assertThrows(AccessDeniedException.class, () -> service.forClient(firm, person.getId()));
        assertThrows(AccessDeniedException.class, () -> service.start(firm, new StartKycRequest(person.getId(), "INDIVIDUAL"), preparer, "P"));
        assertThrows(AccessDeniedException.class, () -> service.openReview(firm, person.getId(), preparer, "P"));
        UUID id = profiles.keySet().iterator().next();
        assertThrows(AccessDeniedException.class, () -> service.get(firm, id));
        assertThrows(AccessDeniedException.class, () -> service.assess(firm, id, low()));
        assertThrows(AccessDeniedException.class, () -> service.approve(firm, id, null, false, approver, "Approver"));
    }

    @Test
    void forClientListsEveryCycleNewestFirst() {
        UUID first = verifiedPerson();
        var review = service.openReview(firm, person.getId(), preparer, "P");
        var all = service.forClient(firm, person.getId());
        assertEquals(List.of(review.id(), first), all.stream().map(KycProfileResponse::id).toList());
    }
}
