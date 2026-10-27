package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.handyflow.platform.facilities.domain.model.FacilitySyncOp;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySyncOpRepository;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Offline sync: each operation is applied at most once, failures are isolated, existing authority checks hold. */
class FacilitySyncServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID me = UUID.randomUUID();
    private final UUID wo = UUID.randomUUID();
    private final UUID inspection = UUID.randomUUID();
    private final List<FacilitySyncOp> recorded = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private final Set<String> authorities = new HashSet<>(Set.of("FACILITIES_TECHNICIAN"));
    private RuntimeException failNextWith;
    private boolean saveRaces;
    private int executions;

    private final FacilitySyncOpRepository repo = (FacilitySyncOpRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FacilitySyncOpRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "exists" -> recorded.stream().anyMatch(o -> o.getOpId().equals(a[2]) && o.getUserId().equals(a[1]));
                case "save" -> {
                    if (saveRaces) throw new DataIntegrityViolationException("uq_facility_sync_op");
                    recorded.add((FacilitySyncOp) a[0]);
                    yield a[0];
                }
                default -> throw new UnsupportedOperationException(m.getName());
            });
    private final FacilitySyncExecutor executor = new FacilitySyncExecutor() {
        @Override public <T> T inNewTransaction(Supplier<T> work) { executions++; return work.get(); }
    };
    private final FacilityWorkOrderService workOrders = new FacilityWorkOrderService(null, null, null, null, null, null, null, null,
            null, null, null, null, null, null) {
        @Override public WorkOrderResponse start(TenantId t, UUID id) { calls.add("start " + id); maybeFail(); return null; }
        @Override public WorkOrderResponse putOnHold(TenantId t, UUID id, HoldWorkOrderRequest r) { calls.add("hold " + r.reason()); maybeFail(); return null; }
        @Override public WorkOrderResponse complete(TenantId t, UUID id, CompleteWorkOrderRequest r) {
            calls.add("complete " + r.completionNotes() + " cost=" + r.cost() + " at=" + r.completedAt()); maybeFail(); return null;
        }
    };
    private final FacilityInspectionService inspections = new FacilityInspectionService(null, null, null, null, null, null, null, null, null, null) {
        @Override public InspectionResponse answer(TenantId t, UUID id, InspectionAnswersRequest r) { calls.add("answers " + r.answers().size()); maybeFail(); return null; }
        @Override public InspectionResponse complete(TenantId t, UUID id, CompleteInspectionRequest r) { calls.add("finish " + r.notes()); maybeFail(); return null; }
    };
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FacilitySyncService service = new FacilitySyncService(repo, executor, workOrders, inspections, policy);

    private void maybeFail() {
        if (failNextWith != null) { RuntimeException e = failNextWith; failNextWith = null; throw e; }
    }

    private SyncRequest.Operation start(UUID opId) {
        return new SyncRequest.Operation(opId, "WORK_ORDER_START", wo, null, null, null, null, null, null);
    }

    private SyncResponse run(SyncRequest.Operation... ops) {
        TenantContext.setUserId(me.toString());
        return service.process(tenant, new SyncRequest(List.of(ops)));
    }

    @Test
    void anOperationIsAppliedOnceAndRecorded() {
        UUID op = UUID.randomUUID();
        SyncResponse r = run(start(op));
        assertEquals("APPLIED", r.results().get(0).status());
        assertEquals(op, r.results().get(0).opId());
        assertEquals(List.of("start " + wo), calls);
        assertEquals(1, recorded.size());
        assertEquals("WORK_ORDER_START", recorded.get(0).getOpType());
    }

    @Test
    void resendingTheSameOperationIsADuplicateAndChangesNothing() {
        UUID op = UUID.randomUUID();
        run(start(op));
        SyncResponse again = run(start(op));
        assertEquals("DUPLICATE", again.results().get(0).status());
        assertEquals(1, calls.size());
        assertEquals(1, recorded.size());
    }

    @Test
    void theSameOpTwiceInOneBatchIsAppliedOnce() {
        UUID op = UUID.randomUUID();
        SyncResponse r = run(start(op), start(op));
        assertEquals(List.of("APPLIED", "DUPLICATE"), r.results().stream().map(SyncResponse.Result::status).toList());
        assertEquals(1, calls.size());
    }

    @Test
    void aRaceBetweenTwoUploadsIsReportedAsADuplicate() {
        saveRaces = true;
        assertEquals("DUPLICATE", run(start(UUID.randomUUID())).results().get(0).status());
    }

    @Test
    void oneRejectedOperationDoesNotStopTheRestAndIsNotRecorded() {
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        failNextWith = new IllegalStateException("Work order is already COMPLETED");
        SyncResponse r = run(start(bad), start(good));
        assertEquals("REJECTED", r.results().get(0).status());
        assertEquals("CONFLICT", r.results().get(0).errorCode());
        assertEquals("Work order is already COMPLETED", r.results().get(0).message());
        assertEquals("APPLIED", r.results().get(1).status());
        assertEquals(1, recorded.size());
        assertEquals(good, recorded.get(0).getOpId());
    }

    @Test
    void aRejectedOperationCanBeResentUnderTheSameIdAndThenApplies() {
        UUID op = UUID.randomUUID();
        failNextWith = new IllegalArgumentException("completedAt is in the future");
        assertEquals("INVALID", run(start(op)).results().get(0).errorCode());
        assertEquals("APPLIED", run(start(op)).results().get(0).status());
    }

    @Test
    void domainErrorsMapToStableCodes() {
        failNextWith = new ResourceNotFoundException("FacilityWorkOrder", wo.toString());
        assertEquals("NOT_FOUND", run(start(UUID.randomUUID())).results().get(0).errorCode());
        failNextWith = new HandyFlowException("not yours", org.springframework.http.HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
        assertEquals("SCOPE_RESTRICTED", run(start(UUID.randomUUID())).results().get(0).errorCode());
        failNextWith = new IllegalStateException("boom") { };
        assertEquals("CONFLICT", run(start(UUID.randomUUID())).results().get(0).errorCode());
        failNextWith = new RuntimeException("database exploded: secret detail");
        SyncResponse.Result r = run(start(UUID.randomUUID())).results().get(0);
        assertEquals("INTERNAL", r.errorCode());
        assertFalse(r.message().contains("secret"));
        assertTrue(recorded.isEmpty());
    }

    @Test
    void unknownOperationTypesAreRejectedWithoutRunningAnything() {
        SyncResponse r = run(new SyncRequest.Operation(UUID.randomUUID(), "DELETE_EVERYTHING", wo, null, null, null, null, null, null));
        assertEquals("UNSUPPORTED_OP", r.results().get(0).errorCode());
        assertEquals(0, executions);
        assertTrue(calls.isEmpty());
    }

    @Test
    void typeNamesAreCaseInsensitive() {
        assertEquals("APPLIED", run(new SyncRequest.Operation(UUID.randomUUID(), "work_order_start", wo, null, null, null, null, null, null))
                .results().get(0).status());
    }

    @Test
    void completeAndHoldPassTheirFieldsThrough() {
        Instant when = Instant.parse("2026-10-27T08:00:00Z");
        run(new SyncRequest.Operation(UUID.randomUUID(), "WORK_ORDER_COMPLETE", wo, "Replaced washer", new BigDecimal("150.00"), when, 30, "Worn", null),
                new SyncRequest.Operation(UUID.randomUUID(), "WORK_ORDER_HOLD", wo, "Waiting for parts", null, null, null, null, null));
        assertEquals(List.of("complete Replaced washer cost=150.00 at=" + when, "hold Waiting for parts"), calls);
    }

    @Test
    void inspectionOperationsNeedAnInspectionAuthorityNotAJobAuthority() {
        UUID a = UUID.randomUUID();
        SyncRequest.Operation answers = new SyncRequest.Operation(UUID.randomUUID(), "INSPECTION_ANSWERS", inspection, null, null, null, null, null,
                List.of(new SyncRequest.Answer(a, "PASS", null), new SyncRequest.Answer(UUID.randomUUID(), "FAIL", "bad")));
        SyncRequest.Operation finish = new SyncRequest.Operation(UUID.randomUUID(), "INSPECTION_COMPLETE", inspection, "done", null, null, null, null, null);
        authorities.clear();
        authorities.add("FACILITIES_INSPECT");
        SyncResponse ok = run(answers, finish);
        assertEquals(List.of("APPLIED", "APPLIED"), ok.results().stream().map(SyncResponse.Result::status).toList());
        assertEquals(List.of("answers 2", "finish done"), calls);
        // the inspector cannot use sync to do technician work
        assertEquals("FORBIDDEN", run(start(UUID.randomUUID())).results().get(0).errorCode());
    }

    @Test
    void aReadOnlyUserCannotChangeAnythingThroughSync() {
        authorities.clear();
        authorities.add("FACILITIES_READ");
        SyncResponse r = run(start(UUID.randomUUID()));
        assertEquals("FORBIDDEN", r.results().get(0).errorCode());
        assertEquals(0, executions);
        assertTrue(recorded.isEmpty());
    }

    @Test
    void inspectionAnswersWithoutAnswersAreInvalid() {
        authorities.add("FACILITIES_INSPECT");
        SyncResponse r = run(new SyncRequest.Operation(UUID.randomUUID(), "INSPECTION_ANSWERS", inspection, null, null, null, null, null, null));
        assertEquals("INVALID", r.results().get(0).errorCode());
    }

    @Test
    void theSameOpIdFromAnotherUserIsADifferentOperation() {
        UUID op = UUID.randomUUID();
        run(start(op));
        TenantContext.setUserId(UUID.randomUUID().toString());
        SyncResponse r = service.process(tenant, new SyncRequest(List.of(start(op))));
        assertEquals("APPLIED", r.results().get(0).status());
        assertEquals(2, recorded.size());
    }
}
