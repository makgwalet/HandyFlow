package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.CharterDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CommitteeCharterServiceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));

    private final List<String> audit = new ArrayList<>();
    private final List<CommitteeCharterRules> store = new ArrayList<>();
    private Committee committee;
    private CommitteeCharterService service;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> m.getName().equals("toString") ? type.getSimpleName() : handler.apply(m.getName(), args == null ? new Object[0] : args));
    }

    @BeforeEach
    void setUp() {
        committee = Committee.create(T, "COM-00001", "Risk committee", CommitteeType.RISK, null, 3, 2, 12, null, "me");
        CommitteeRepository committees = proxy(CommitteeRepository.class, (n, a) -> n.equals("findForTenant") ? (a[1].equals(committee.getId()) ? Optional.of(committee) : Optional.empty()) : null);
        CommitteeCharterRulesRepository charters = proxy(CommitteeCharterRulesRepository.class, (n, a) -> switch (n) {
            case "history" -> store.stream().sorted((x, y) -> y.getVersionNo() - x.getVersionNo()).toList();
            case "latestVersion" -> store.stream().mapToInt(CommitteeCharterRules::getVersionNo).max().orElse(0);
            case "save" -> { store.add((CommitteeCharterRules) a[0]); yield a[0]; }
            default -> null;
        });
        RegulatoryAuditService trail = new RegulatoryAuditService(null) {
            @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID a, String n) { audit.add(ev + "|" + d); }
        };
        service = new CommitteeCharterService(committees, charters, trail);
    }

    private RecordCharterRequest req(boolean casting, WrittenMode mode, boolean quorum, int pct, LocalDate effective) {
        return new RecordCharterRequest("Charter 2026", TODAY.minusDays(20), effective, casting, mode, quorum, pct, "Adopted at the board meeting");
    }

    @Test @DisplayName("Before anything is recorded the view says the house rules apply")
    void nothingRecorded() {
        CharterView v = service.view(T, committee.getId());
        assertFalse(v.recorded());
        assertNull(v.currentVersion());
        assertEquals(75, v.specialPercent());
        assertTrue(v.history().isEmpty());
        assertTrue(v.summary().get(0).contains("house rules"));
        assertEquals(2, v.allowedWrittenThresholds().size());
    }

    @Test @DisplayName("Recording a version numbers it, puts it in force, and writes the audit trail with the reason")
    void records() {
        CharterView v = service.record(T, committee.getId(), req(true, WrittenMode.UNANIMOUS, false, 80, TODAY), null, "Admin");
        assertTrue(v.recorded());
        assertEquals(1, v.currentVersion());
        assertEquals(80, v.specialPercent());
        assertEquals(List.of(ApprovalThreshold.UNANIMOUS), v.allowedWrittenThresholds());
        assertTrue(v.history().get(0).inForce());
        assertEquals(1, audit.size());
        assertTrue(audit.get(0).startsWith("CHARTER_RULES_RECORDED|"));
        assertTrue(audit.get(0).contains("first version") && audit.get(0).contains("Adopted at the board meeting"));
    }

    @Test @DisplayName("A second version keeps the first, takes over, and the audit text says what changed")
    void changes() {
        service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 75, TODAY.minusDays(2)), null, "Admin");
        CharterView v = service.record(T, committee.getId(), req(false, WrittenMode.EITHER, true, 75, TODAY), null, "Admin");
        assertEquals(2, v.currentVersion());
        assertEquals(2, v.history().size());
        assertFalse(v.history().get(1).inForce());
        assertFalse(v.castingVote());
        assertTrue(audit.get(1).contains("changed from casting vote yes"));
    }

    @Test @DisplayName("Recording the rules already in force is refused")
    void nothingToRecord() {
        service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 75, TODAY), null, "Admin");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 75, TODAY), null, "Admin"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(1, store.size());
    }

    @Test @DisplayName("A future version is shown in the history but not in force; a version dated before it is refused")
    void futureVersion() {
        service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 75, TODAY), null, "Admin");
        CharterView v = service.record(T, committee.getId(), req(false, WrittenMode.EITHER, true, 75, TODAY.plusDays(10)), null, "Admin");
        assertEquals(1, v.currentVersion());
        assertTrue(v.castingVote());
        assertEquals(2, v.history().size());
        assertThrows(HandyFlowException.class, () -> service.record(T, committee.getId(), req(true, WrittenMode.NOT_ALLOWED, true, 75, TODAY.plusDays(5)), null, "Admin"));
        assertEquals(2, store.size());
    }

    @Test @DisplayName("Invalid input is a 400 and saves nothing; an unknown committee is not found; a dissolved committee is refused")
    void refusals() {
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 40, TODAY), null, "Admin"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        HandyFlowException e2 = assertThrows(HandyFlowException.class, () -> service.record(T, committee.getId(), new RecordCharterRequest(null, TODAY, TODAY.minusDays(1), true, WrittenMode.EITHER, true, 75, "Adopted at the board meeting"), null, "Admin"));
        assertEquals(HttpStatus.BAD_REQUEST, e2.getStatus());
        assertTrue(store.isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.view(T, UUID.randomUUID()));
        committee.dissolve("Merged into the board, per resolution");
        assertThrows(HandyFlowException.class, () -> service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 75, TODAY), null, "Admin"));
    }

    @Test @DisplayName("When a future version is waiting, the old rules can be recorded again to take effect after it (the same as today's, but not 'nothing')")
    void revertAfterFuture() {
        service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 75, TODAY), null, "Admin");
        service.record(T, committee.getId(), req(false, WrittenMode.EITHER, true, 75, TODAY.plusDays(10)), null, "Admin");
        CharterView v = service.record(T, committee.getId(), req(true, WrittenMode.EITHER, true, 75, TODAY.plusDays(20)), null, "Admin");
        assertEquals(3, v.history().size());
        assertEquals(1, v.currentVersion());
    }
}
