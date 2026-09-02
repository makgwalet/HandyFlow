package za.co.handyflow.platform.trainingprovider.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainProvSessionTest {

    private TrainProvSession newPublicSession() {
        return TrainProvSession.create(TenantId.generate(), UUID.randomUUID(), "PUBLIC", null,
                LocalDate.now().plusDays(7), LocalDate.now().plusDays(8), "Venue", "Trainer", 20, null);
    }

    private TrainProvSession newClosedSession(UUID clientId) {
        return TrainProvSession.create(TenantId.generate(), UUID.randomUUID(), "CLOSED", clientId,
                LocalDate.now().plusDays(7), LocalDate.now().plusDays(8), "Client site", "Trainer", null, null);
    }

    @Test
    void publicSessionRejectsAClientId() {
        TenantId tenantId = TenantId.generate();
        UUID courseId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> TrainProvSession.create(tenantId, courseId, "PUBLIC", clientId,
                LocalDate.now(), LocalDate.now().plusDays(1), null, null, null, null));
    }

    @Test
    void closedSessionRequiresAClientId() {
        TenantId tenantId = TenantId.generate();
        UUID courseId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> TrainProvSession.create(tenantId, courseId, "CLOSED", null,
                LocalDate.now(), LocalDate.now().plusDays(1), null, null, null, null));
    }

    @Test
    void isClosedReflectsSessionType() {
        assertFalse(newPublicSession().isClosed());
        assertTrue(newClosedSession(UUID.randomUUID()).isClosed());
    }

    @Test
    void newSessionStartsScheduledAndAcceptsEnrollment() {
        TrainProvSession session = newPublicSession();
        assertEquals("SCHEDULED", session.getStatus());
        assertTrue(session.acceptsEnrollment());
    }

    @Test
    void startThenCompleteFollowsLifecycle() {
        TrainProvSession session = newPublicSession();
        session.start();
        assertEquals("IN_PROGRESS", session.getStatus());
        session.complete();
        assertEquals("COMPLETED", session.getStatus());
    }

    @Test
    void cannotUpdateOrRescheduleATerminalSession() {
        TrainProvSession session = newPublicSession();
        session.cancel("test");
        assertThrows(IllegalStateException.class, () -> session.update(null, null, null, null));
        assertThrows(IllegalStateException.class, () -> session.reschedule(LocalDate.now(), LocalDate.now().plusDays(1)));
    }

    @Test
    void isFullRespectsCapacityAndUnlimitedWhenNull() {
        TrainProvSession capped = newPublicSession();
        assertFalse(capped.isFull(19));
        assertTrue(capped.isFull(20));

        TrainProvSession unlimited = newClosedSession(UUID.randomUUID());
        assertFalse(unlimited.isFull(10_000));
    }
}
