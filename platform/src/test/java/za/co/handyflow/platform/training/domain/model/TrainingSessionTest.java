package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainingSessionTest {

    private TrainingSession newSession() {
        return TrainingSession.create(TenantId.generate(), UUID.randomUUID(),
                LocalDate.now().plusDays(7), LocalDate.now().plusDays(8), "Boardroom A", "Jane Trainer", 10, null);
    }

    @Test
    void newSessionStartsScheduled() {
        TrainingSession session = newSession();
        assertEquals("SCHEDULED", session.getStatus());
        assertTrue(session.acceptsEnrollment());
    }

    @Test
    void endDateBeforeStartDateThrows() {
        TenantId tenantId = TenantId.generate();
        UUID courseId = UUID.randomUUID();
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = start.minusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> TrainingSession.create(tenantId, courseId, start, end, null, null, null, null));
    }

    @Test
    void startThenCompleteFollowsLifecycle() {
        TrainingSession session = newSession();
        session.start();
        assertEquals("IN_PROGRESS", session.getStatus());
        session.complete();
        assertEquals("COMPLETED", session.getStatus());
    }

    @Test
    void cannotStartATerminalSession() {
        TrainingSession session = newSession();
        session.cancel("Trainer unavailable");
        assertThrows(IllegalStateException.class, session::start);
    }

    @Test
    void cannotUpdateOrRescheduleATerminalSession() {
        TrainingSession session = newSession();
        session.complete();
        assertThrows(IllegalStateException.class, () -> session.update(null, null, null, null));
        assertThrows(IllegalStateException.class,
                () -> session.reschedule(LocalDate.now(), LocalDate.now().plusDays(1)));
    }

    @Test
    void isFullRespectsCapacityAndUnlimitedWhenNull() {
        TrainingSession capped = newSession();
        assertFalse(capped.isFull(9));
        assertTrue(capped.isFull(10));
        assertTrue(capped.isFull(11));

        TrainingSession unlimited = TrainingSession.create(TenantId.generate(), UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(1), null, null, null, null);
        assertFalse(unlimited.isFull(10_000));
    }
}
