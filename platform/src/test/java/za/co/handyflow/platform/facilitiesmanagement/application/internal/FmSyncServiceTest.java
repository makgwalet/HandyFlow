package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSyncOp;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSyncOpRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
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

/** FM offline sync: each operation is applied at most once, failures are isolated, existing authority checks hold. */
class FmSyncServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID me = UUID.randomUUID();
    private final UUID wo = UUID.randomUUID();
    private final List<FmSyncOp> recorded = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private final Set<String> authorities = new HashSet<>(Set.of("FACILITIESMANAGEMENT_TECHNICIAN"));
    private RuntimeException failNextWith;
    private boolean saveRaces;
    private int executions;

    private final FmSyncOpRepository repo = (FmSyncOpRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmSyncOpRepository.class}, (p, m, a) -> switch (m.getName()) {
                case "exists" -> recorded.stream().anyMatch(o -> o.getOpId().equals(a[2]) && o.getUserId().equals(a[1]));
                case "save" -> {
                    if (saveRaces) throw new DataIntegrityViolationException("uq_fm_sync_op");
                    recorded.add((FmSyncOp) a[0]);
                    yield a[0];
                }
                default -> throw new UnsupportedOperationException(m.getName());
            });
    private final FmSyncExecutor executor = new FmSyncExecutor() {
        @Override public <T> T inNewTransaction(Supplier<T> work) { executions++; return work.get(); }
    };
    private final FmWorkOrderService workOrders = new FmWorkOrderService(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) {
        @Override public FmWorkOrderResponse start(TenantId t, UUID id) { calls.add("start " + id); maybeFail(); return null; }
        @Override public FmWorkOrderResponse putOnHold(TenantId t, UUID id, HoldFmWorkOrderRequest r) {
            calls.add("hold " + r.reason() + " pause=" + r.pauseSla()); maybeFail(); return null;
        }
        @Override public FmWorkOrderResponse complete(TenantId t, UUID id, CompleteFmWorkOrderRequest r) {
            calls.add("complete " + r.completionNotes() + " cost=" + r.cost() + " at=" + r.completedAt()); maybeFail(); return null;
        }
    };
    private final FmWorkOrderCostService costs = new FmWorkOrderCostService(null, null, null, null) {
        @Override public FmCostLineResponse add(TenantId t, UUID id, AddFmCostLineRequest r) {
            calls.add("cost " + r.costType() + " " + r.description() + " " + r.quantity() + "x" + r.unitCost()
                    + " charge=" + r.unitCharge() + " billable=" + r.billable());
            maybeFail();
            return null;
        }
    };
    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FmSyncService service = new FmSyncService(repo, executor, workOrders, costs, policy);

    private void maybeFail() {
        if (failNextWith != null) { RuntimeException e = failNextWith; failNextWith = null; throw e; }
    }

    private FmSyncRequest.Operation start(UUID opId) {
        return new FmSyncRequest.Operation(opId, "WORK_ORDER_START", wo, null, null, null, null, null, null, null, null);
    }

    private FmSyncRequest.Operation cost(UUID opId, String type, String desc, String qty, String unit) {
        return new FmSyncRequest.Operation(opId, "COST_LINE_ADD", wo, desc, null, null, null, null, type,
                qty == null ? null : new BigDecimal(qty), unit == null ? null : new BigDecimal(unit));
    }

    private FmSyncResponse run(FmSyncRequest.Operation... ops) {
        TenantContext.setUserId(me.toString());
        return service.process(tenant, new FmSyncRequest(List.of(ops)));
    }

    @Test
    void anOperationIsAppliedOnceAndRecorded() {
        UUID op = UUID.randomUUID();
        FmSyncResponse r = run(start(op));
        assertEquals("APPLIED", r.results().get(0).status());
        assertEquals(op, r.results().get(0).opId());
        assertEquals(List.of("start " + wo), calls);
        assertEquals("WORK_ORDER_START", recorded.get(0).getOpType());
    }

    @Test
    void resendingTheSameOperationIsADuplicateAndChangesNothing() {
        UUID op = UUID.randomUUID();
        run(start(op));
        assertEquals("DUPLICATE", run(start(op)).results().get(0).status());
        assertEquals(1, calls.size());
        assertEquals(1, recorded.size());
    }

    @Test
    void theSameOpTwiceInOneBatchIsAppliedOnce() {
        UUID op = UUID.randomUUID();
        assertEquals(List.of("APPLIED", "DUPLICATE"), run(start(op), start(op)).results().stream().map(FmSyncResponse.Result::status).toList());
        assertEquals(1, calls.size());
    }

    @Test
    void aRaceBetweenTwoUploadsIsReportedAsADuplicate() {
        saveRaces = true;
        assertEquals("DUPLICATE", run(start(UUID.randomUUID())).results().get(0).status());
    }

    @Test
    void oneRejectedOperationDoesNotStopTheRestAndIsNotRecorded() {
        UUID good = UUID.randomUUID();
        failNextWith = new IllegalStateException("Work order is already COMPLETED");
        FmSyncResponse r = run(start(UUID.randomUUID()), start(good));
        assertEquals("REJECTED", r.results().get(0).status());
        assertEquals("CONFLICT", r.results().get(0).errorCode());
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
    void domainErrorsMapToStableCodesAndInternalOnesLeakNothing() {
        failNextWith = new ResourceNotFoundException("FmWorkOrder", wo.toString());
        assertEquals("NOT_FOUND", run(start(UUID.randomUUID())).results().get(0).errorCode());
        failNextWith = new HandyFlowException("not yours", org.springframework.http.HttpStatus.FORBIDDEN, "COST_LINE_PRICING_RESTRICTED");
        assertEquals("COST_LINE_PRICING_RESTRICTED", run(start(UUID.randomUUID())).results().get(0).errorCode());
        failNextWith = new RuntimeException("database exploded: secret detail");
        FmSyncResponse.Result r = run(start(UUID.randomUUID())).results().get(0);
        assertEquals("INTERNAL", r.errorCode());
        assertFalse(r.message().contains("secret"));
        assertTrue(recorded.isEmpty());
    }

    @Test
    void unknownTypesAreRejectedWithoutRunningAnything() {
        FmSyncResponse r = run(new FmSyncRequest.Operation(UUID.randomUUID(), "DELETE_EVERYTHING", wo, null, null, null, null, null, null, null, null));
        assertEquals("UNSUPPORTED_OP", r.results().get(0).errorCode());
        assertEquals(0, executions);
    }

    @Test
    void completeAndHoldPassTheirFieldsThroughAndAHoldNeverPausesTheSlaClock() {
        Instant when = Instant.parse("2026-10-27T08:00:00Z");
        run(new FmSyncRequest.Operation(UUID.randomUUID(), "WORK_ORDER_COMPLETE", wo, "Replaced washer", new BigDecimal("150.00"), when, 30, "Worn", null, null, null),
                new FmSyncRequest.Operation(UUID.randomUUID(), "WORK_ORDER_HOLD", wo, "Waiting for parts", null, null, null, null, null, null, null));
        assertEquals(List.of("complete Replaced washer cost=150.00 at=" + when, "hold Waiting for parts pause=false"), calls);
    }

    @Test
    void aCostLineIsAddedWithoutAnyPricingFromTheDevice() {
        run(cost(UUID.randomUUID(), "PARTS", "15mm washer", "2", "12.50"));
        assertEquals(List.of("cost PARTS 15mm washer 2x12.50 charge=null billable=null"), calls);
    }

    @Test
    void aCostLineNeedsItsFields() {
        assertEquals("INVALID", run(cost(UUID.randomUUID(), null, "x", "1", "1")).results().get(0).errorCode());
        assertEquals("INVALID", run(cost(UUID.randomUUID(), "PARTS", " ", "1", "1")).results().get(0).errorCode());
        assertEquals("INVALID", run(cost(UUID.randomUUID(), "PARTS", "x", null, "1")).results().get(0).errorCode());
        assertEquals("INVALID", run(cost(UUID.randomUUID(), "PARTS", "x", "1", null)).results().get(0).errorCode());
        assertTrue(calls.isEmpty());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void aCostLineRefusedByTheCostServiceIsReportedNotRecorded() {
        failNextWith = new HandyFlowException("Work order is not editable", org.springframework.http.HttpStatus.CONFLICT, "WORK_ORDER_LOCKED");
        FmSyncResponse.Result r = run(cost(UUID.randomUUID(), "PARTS", "washer", "1", "1")).results().get(0);
        assertEquals("REJECTED", r.status());
        assertEquals("WORK_ORDER_LOCKED", r.errorCode());
        assertTrue(recorded.isEmpty());
    }

    @Test
    void aReadOnlyUserCannotChangeAnythingThroughSync() {
        authorities.clear();
        authorities.add("FACILITIESMANAGEMENT_READ");
        FmSyncResponse r = run(start(UUID.randomUUID()));
        assertEquals("FORBIDDEN", r.results().get(0).errorCode());
        assertEquals(0, executions);
        assertTrue(recorded.isEmpty());
    }

    @Test
    void theSameOpIdFromAnotherUserIsADifferentOperation() {
        UUID op = UUID.randomUUID();
        run(start(op));
        TenantContext.setUserId(UUID.randomUUID().toString());
        assertEquals("APPLIED", service.process(tenant, new FmSyncRequest(List.of(start(op)))).results().get(0).status());
        assertEquals(2, recorded.size());
    }
}
