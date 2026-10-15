package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.EarthChecklistService;
import za.co.handyflow.platform.earthmoving.application.internal.EarthmovingAccess;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthChecklistQuestionRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthChecklistVersionRepository;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthChecklistServiceTest {
    EarthChecklistVersionRepository versions = mock(EarthChecklistVersionRepository.class);
    EarthChecklistQuestionRepository questions = mock(EarthChecklistQuestionRepository.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthChecklistService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    Map<UUID, List<EarthChecklistQuestion>> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new EarthChecklistService(versions, questions, access, trail);
        when(versions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(questions.save(any())).thenAnswer(i -> {
            EarthChecklistQuestion q = i.getArgument(0);
            stored.computeIfAbsent(q.getVersionId(), k -> new ArrayList<>()).add(q);
            return q;
        });
        when(questions.findForVersion(any(), any())).thenAnswer(i -> stored.getOrDefault(i.<UUID>getArgument(1), List.of()));
        doAnswer(i -> { stored.remove(i.<UUID>getArgument(1)); return null; }).when(questions).deleteForVersion(any(), any());
        when(versions.findByType(any(), any())).thenReturn(List.of());
        when(versions.findDraft(any(), any())).thenReturn(Optional.empty());
        when(versions.findInForce(any(), any(), any(), any())).thenReturn(List.of());
        granted.add("EARTHMOVING_ADMIN");
        granted.add("EARTHMOVING_READ");
    }

    ChecklistQuestionDto dto(String text) { return new ChecklistQuestionDto(null, null, text, "PASS_FAIL", true, false, null, null, null, "DEFECT"); }

    List<ChecklistQuestionDto> builtInPlus(ChecklistQuestionDto... extra) {
        List<ChecklistQuestionDto> l = new ArrayList<>();
        for (String b : InspectionRules.CHECKLIST) l.add(dto(b));
        l.addAll(List.of(extra));
        return l;
    }

    @Test void withNothingPublishedAWalkRoundTypeUsesTheBuiltInList() {
        ChecklistRules.Active a = service.resolve(tenant, "PRE_USE", null);
        assertTrue(a.builtInDefault());
        assertEquals(10, a.questions().size());
    }

    @Test void maintenanceHasNoBuiltInListSoItNeedsOnePublishedFirst() {
        assertThrows(IllegalStateException.class, () -> service.resolve(tenant, "MAINTENANCE", null));
    }

    @Test void aDraftStartsFromTheBuiltInListWithTheNextVersionNumber() {
        when(versions.latestNumber(tenant, "PRE_USE")).thenReturn(0);
        ChecklistVersionResponse r = service.createDraft(tenant, "PRE_USE", actor);
        assertEquals(1, r.versionNo());
        assertEquals("DRAFT", r.status());
        assertEquals(10, r.questions().size());
        assertTrue(r.questions().stream().allMatch(ChecklistQuestionDto::builtIn));
    }

    @Test void onlyOneDraftPerTypeAtATime() {
        when(versions.findDraft(tenant, "PRE_USE")).thenReturn(Optional.of(EarthChecklistVersion.draft(tenant, "PRE_USE", 2, false, actor)));
        assertThrows(IllegalStateException.class, () -> service.createDraft(tenant, "PRE_USE", actor));
    }

    @Test void writingAndPublishingChecklistsNeedsAdmin() {
        granted.remove("EARTHMOVING_ADMIN");
        assertThrows(HandyFlowException.class, () -> service.createDraft(tenant, "PRE_USE", actor));
        assertThrows(HandyFlowException.class, () -> service.saveDraft(tenant, UUID.randomUUID(), new SaveChecklistDraftRequest(false, null, builtInPlus()), actor));
        assertThrows(HandyFlowException.class, () -> service.publish(tenant, UUID.randomUUID(), null, actor));
        assertThrows(HandyFlowException.class, () -> service.discard(tenant, UUID.randomUUID(), actor));
    }

    @Test void savingReplacesTheQuestionsAndAddsToTheBuiltInList() {
        EarthChecklistVersion v = EarthChecklistVersion.draft(tenant, "PRE_USE", 1, false, actor);
        when(versions.findOwned(tenant, v.getId())).thenReturn(Optional.of(v));
        ChecklistVersionResponse r = service.saveDraft(tenant, v.getId(), new SaveChecklistDraftRequest(true, "Added greasing", builtInPlus(dto("Greased today"))), actor);
        assertEquals(11, r.questions().size());
        assertTrue(r.requiresSignoff());
        assertEquals("Added greasing", r.note());
        verify(questions).deleteForVersion(tenant, v.getId());
    }

    @Test void aDraftThatDropsABuiltInItemIsRefused() {
        EarthChecklistVersion v = EarthChecklistVersion.draft(tenant, "PRE_USE", 1, false, actor);
        when(versions.findOwned(tenant, v.getId())).thenReturn(Optional.of(v));
        List<ChecklistQuestionDto> short9 = builtInPlus();
        short9.remove(0);
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(tenant, v.getId(), new SaveChecklistDraftRequest(false, null, short9), actor));
        verify(questions, never()).deleteForVersion(any(), any());
    }

    @Test void publishingFixesTheVersionAndAPublishedVersionCannotBeChangedOrDiscarded() {
        EarthChecklistVersion v = EarthChecklistVersion.draft(tenant, "SAFETY", 1, false, actor);
        when(versions.findOwned(tenant, v.getId())).thenReturn(Optional.of(v));
        service.saveDraft(tenant, v.getId(), new SaveChecklistDraftRequest(false, null, List.of(dto("Extinguisher charged"))), actor);
        ChecklistVersionResponse r = service.publish(tenant, v.getId(), new PublishChecklistRequest(today.plusDays(3)), actor);
        assertEquals("PUBLISHED", r.status());
        assertEquals(today.plusDays(3), r.effectiveFrom());
        assertThrows(IllegalStateException.class, () -> service.saveDraft(tenant, v.getId(), new SaveChecklistDraftRequest(false, null, List.of(dto("Other"))), actor));
        assertThrows(IllegalStateException.class, () -> service.discard(tenant, v.getId(), actor));
        assertThrows(IllegalStateException.class, () -> service.publish(tenant, v.getId(), null, actor));
    }

    @Test void publishingCannotStartInThePast() {
        EarthChecklistVersion v = EarthChecklistVersion.draft(tenant, "SAFETY", 1, false, actor);
        when(versions.findOwned(tenant, v.getId())).thenReturn(Optional.of(v));
        service.saveDraft(tenant, v.getId(), new SaveChecklistDraftRequest(false, null, List.of(dto("Extinguisher charged"))), actor);
        assertThrows(IllegalArgumentException.class, () -> service.publish(tenant, v.getId(), new PublishChecklistRequest(today.minusDays(1)), actor));
        assertTrue(v.isDraft());
    }

    @Test void theVersionInForceIsUsedAndAnUnstartedOrDraftVersionCannotBePicked() {
        EarthChecklistVersion live = EarthChecklistVersion.draft(tenant, "SAFETY", 1, true, actor);
        when(versions.findOwned(tenant, live.getId())).thenReturn(Optional.of(live));
        service.saveDraft(tenant, live.getId(), new SaveChecklistDraftRequest(true, null, List.of(dto("Extinguisher charged"))), actor);
        assertThrows(IllegalStateException.class, () -> service.resolve(tenant, "SAFETY", live.getId()));
        service.publish(tenant, live.getId(), null, actor);
        ChecklistRules.Active a = service.resolve(tenant, "SAFETY", live.getId());
        assertEquals(1, a.versionNo());
        assertTrue(a.requiresSignoff());
        assertThrows(IllegalArgumentException.class, () -> service.resolve(tenant, "PRE_USE", live.getId()));
    }
}
