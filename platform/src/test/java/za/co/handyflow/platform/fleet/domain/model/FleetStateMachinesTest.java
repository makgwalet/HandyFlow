package za.co.handyflow.platform.fleet.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The legal moves of every fleet status. Terminal states stay terminal; nothing may skip a step it should not. */
class FleetStateMachinesTest {

    @Test void workOrderFollowsQuoteApproveWorkComplete() {
        assertTrue(WorkOrderStatus.OPEN.canTransitionTo(WorkOrderStatus.QUOTED));
        assertTrue(WorkOrderStatus.QUOTED.canTransitionTo(WorkOrderStatus.APPROVED));
        assertTrue(WorkOrderStatus.APPROVED.canTransitionTo(WorkOrderStatus.IN_PROGRESS));
        assertTrue(WorkOrderStatus.IN_PROGRESS.canTransitionTo(WorkOrderStatus.ON_HOLD));
        assertTrue(WorkOrderStatus.ON_HOLD.canTransitionTo(WorkOrderStatus.IN_PROGRESS));
        assertTrue(WorkOrderStatus.IN_PROGRESS.canTransitionTo(WorkOrderStatus.COMPLETED));
    }

    @Test void aQuotedWorkOrderCannotStartWorkUntilApproved() {
        assertFalse(WorkOrderStatus.QUOTED.canTransitionTo(WorkOrderStatus.IN_PROGRESS));
        assertFalse(WorkOrderStatus.QUOTED.canTransitionTo(WorkOrderStatus.COMPLETED));
        assertFalse(WorkOrderStatus.OPEN.canTransitionTo(WorkOrderStatus.COMPLETED));
        assertFalse(WorkOrderStatus.APPROVED.canTransitionTo(WorkOrderStatus.COMPLETED));
    }

    @Test void aRejectedQuoteGoesBackToOpen() {
        assertTrue(WorkOrderStatus.QUOTED.canTransitionTo(WorkOrderStatus.OPEN));
    }

    @Test void completedAndCancelledWorkOrdersAreFinal() {
        for (WorkOrderStatus end : new WorkOrderStatus[]{WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED}) {
            for (WorkOrderStatus to : WorkOrderStatus.values()) assertFalse(end.canTransitionTo(to), end + " -> " + to);
        }
    }

    @Test void anyLiveWorkOrderCanBeCancelledExceptOnesAlreadyEnded() {
        for (WorkOrderStatus s : WorkOrderStatus.ACTIVE) assertTrue(s.canTransitionTo(WorkOrderStatus.CANCELLED), s.name());
        assertTrue(WorkOrderStatus.WORKING.containsAll(java.util.List.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD)));
    }

    @Test void issuesCanBeReopenedButNotJumpedBetweenClosedStates() {
        assertTrue(IssueStatus.RESOLVED.canTransitionTo(IssueStatus.OPEN));
        assertTrue(IssueStatus.DISMISSED.canTransitionTo(IssueStatus.OPEN));
        assertFalse(IssueStatus.RESOLVED.canTransitionTo(IssueStatus.DISMISSED));
        assertFalse(IssueStatus.DISMISSED.canTransitionTo(IssueStatus.RESOLVED));
        assertFalse(IssueStatus.OPEN.canTransitionTo(IssueStatus.OPEN));
    }

    @Test void aClosedAccidentReopensToAssessmentAndAWriteOffIsFinal() {
        assertTrue(AccidentStatus.CLOSED.canTransitionTo(AccidentStatus.UNDER_ASSESSMENT));
        assertFalse(AccidentStatus.CLOSED.canTransitionTo(AccidentStatus.IN_REPAIR));
        for (AccidentStatus to : AccidentStatus.values()) assertFalse(AccidentStatus.WRITTEN_OFF.canTransitionTo(to));
    }

    @Test void aClaimMovesForwardAndCanBeResubmittedAfterRejection() {
        assertTrue(ClaimStatus.NOT_CLAIMED.canTransitionTo(ClaimStatus.SUBMITTED));
        assertFalse(ClaimStatus.NOT_CLAIMED.canTransitionTo(ClaimStatus.SETTLED));
        assertFalse(ClaimStatus.SUBMITTED.canTransitionTo(ClaimStatus.SETTLED));
        assertTrue(ClaimStatus.APPROVED.canTransitionTo(ClaimStatus.SETTLED));
        assertTrue(ClaimStatus.REJECTED.canTransitionTo(ClaimStatus.SUBMITTED));
        assertFalse(ClaimStatus.SETTLED.canTransitionTo(ClaimStatus.REJECTED));
    }

    @Test void savingTheSameClaimStatusAgainIsAllowedSoDetailsCanBeEdited() {
        for (ClaimStatus s : ClaimStatus.values()) assertTrue(s.canTransitionTo(s), s.name());
    }

    @Test void onlyLodgedUnresolvedClaimsAreUnresolved() {
        assertTrue(ClaimStatus.SUBMITTED.isUnresolved());
        assertTrue(ClaimStatus.ASSESSING.isUnresolved());
        assertTrue(ClaimStatus.APPROVED.isUnresolved());
        assertFalse(ClaimStatus.NOT_CLAIMED.isUnresolved());
        assertFalse(ClaimStatus.REJECTED.isUnresolved());
        assertFalse(ClaimStatus.SETTLED.isUnresolved());
    }

    @Test void aStolenVehicleCanBeRecoveredOrWrittenOffButNotSold() {
        assertTrue(VehicleLifecycleStatus.STOLEN.canTransitionTo(VehicleLifecycleStatus.ACTIVE));
        assertTrue(VehicleLifecycleStatus.STOLEN.canTransitionTo(VehicleLifecycleStatus.WRITTEN_OFF));
        assertFalse(VehicleLifecycleStatus.STOLEN.canTransitionTo(VehicleLifecycleStatus.SOLD));
    }

    @Test void soldTransferredAndDisposedVehiclesNeverComeBack() {
        for (VehicleLifecycleStatus end : new VehicleLifecycleStatus[]{VehicleLifecycleStatus.SOLD, VehicleLifecycleStatus.TRANSFERRED, VehicleLifecycleStatus.DISPOSED}) {
            for (VehicleLifecycleStatus to : VehicleLifecycleStatus.values()) assertFalse(end.canTransitionTo(to), end + " -> " + to);
        }
    }

    @Test void onlyAnActiveVehicleIsOperatingAndEveryStateIsCovered() {
        for (VehicleLifecycleStatus s : VehicleLifecycleStatus.values()) {
            assertEquals(s == VehicleLifecycleStatus.ACTIVE, s.isOperating(), s.name());
            assertNotNull(s.canTransitionTo(VehicleLifecycleStatus.ACTIVE)); // every status has a rule entry (no NullPointerException)
        }
        assertTrue(VehicleLifecycleStatus.WRITTEN_OFF.isOutOfFleet());
        assertFalse(VehicleLifecycleStatus.STOLEN.isOutOfFleet());
    }

    @Test void tyresAndBatteriesCannotLeaveTheScrapHeap() {
        for (TyreStatus to : TyreStatus.values()) assertFalse(TyreStatus.SCRAPPED.canTransitionTo(to));
        for (BatteryStatus to : BatteryStatus.values()) assertFalse(BatteryStatus.SCRAPPED.canTransitionTo(to));
        assertTrue(BatteryStatus.FAULTY.canTransitionTo(BatteryStatus.IN_STOCK));
        assertFalse(BatteryStatus.IN_STOCK.canTransitionTo(BatteryStatus.FAULTY));
        assertFalse(TyreStatus.IN_STOCK.canTransitionTo(TyreStatus.AWAITING_RETREAD));
    }
}
