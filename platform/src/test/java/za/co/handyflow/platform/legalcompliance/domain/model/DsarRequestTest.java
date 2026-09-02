package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DsarRequestTest {

    private final TenantId tenantId = TenantId.generate();

    private DsarRequest newRequest(LocalDate receivedDate) {
        return DsarRequest.create(tenantId, "DSAR-00001", DsarRequestType.ACCESS, DataCategory.CUSTOMER,
                "Jane Requester", "jane@example.com", "+27821234567", receivedDate, UUID.randomUUID());
    }

    @Test
    @DisplayName("create() defaults dueDate to receivedDate + 30 days and starts RECEIVED")
    void createDefaultsDueDate() {
        LocalDate received = LocalDate.now();
        DsarRequest r = newRequest(received);
        assertEquals(received.plusDays(30), r.getDueDate());
        assertEquals(DsarStatus.RECEIVED, r.getStatus());
    }

    @Test
    @DisplayName("assign() moves RECEIVED to IN_PROGRESS")
    void assignMovesReceivedToInProgress() {
        DsarRequest r = newRequest(LocalDate.now());
        r.assign(UUID.randomUUID(), "Staff Member");
        assertEquals(DsarStatus.IN_PROGRESS, r.getStatus());
        assertEquals("Staff Member", r.getAssignedToUserName());
    }

    @Test
    @DisplayName("assign() does not regress an already IN_PROGRESS request")
    void assignDoesNotRegressInProgress() {
        DsarRequest r = newRequest(LocalDate.now());
        r.assign(UUID.randomUUID(), "First Assignee");
        r.assign(UUID.randomUUID(), "Second Assignee");
        assertEquals(DsarStatus.IN_PROGRESS, r.getStatus());
        assertEquals("Second Assignee", r.getAssignedToUserName());
    }

    @Test
    @DisplayName("complete() sets COMPLETED and completedDate")
    void completeSetsCompletedAndDate() {
        DsarRequest r = newRequest(LocalDate.now());
        r.complete("Export provided");
        assertEquals(DsarStatus.COMPLETED, r.getStatus());
        assertEquals(LocalDate.now(), r.getCompletedDate());
        assertEquals("Export provided", r.getResolutionNotes());
    }

    @Test
    @DisplayName("reject()/withdraw()/complete() all throw once the request is already closed")
    void terminalActionsThrowWhenAlreadyClosed() {
        DsarRequest r = newRequest(LocalDate.now());
        r.reject("Invalid request");
        assertThrows(IllegalStateException.class, () -> r.complete("too late"));
        assertThrows(IllegalStateException.class, () -> r.withdraw("too late"));
        assertThrows(IllegalStateException.class, () -> r.assign(UUID.randomUUID(), "Nobody"));
    }

    @Test
    @DisplayName("isOverdue() is true only while open and past dueDate")
    void isOverdueOnlyWhileOpenAndPastDue() {
        DsarRequest r = newRequest(LocalDate.now().minusDays(40)); // dueDate = received + 30, so already past
        assertTrue(r.isOverdue(LocalDate.now()));

        r.complete("done");
        assertFalse(r.isOverdue(LocalDate.now()));
    }

    @Test
    @DisplayName("softDelete() marks the request deleted")
    void softDeleteMarksDeleted() {
        DsarRequest r = newRequest(LocalDate.now());
        assertFalse(r.isDeleted());
        r.softDelete(UUID.randomUUID());
        assertTrue(r.isDeleted());
    }
}
