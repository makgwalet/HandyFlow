package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourceBookingRulesTest {

    private static final UUID SITE = UUID.randomUUID();

    @Test
    void refusesWhatCannotBeBookedAtAll() {
        assertEquals("A cancelled slot cannot have resources booked", ResourceBookingRules.bookProblem("IN_SERVICE", SITE, SITE, "CANCELLED", 1, 2));
        assertEquals("That resource is not in service", ResourceBookingRules.bookProblem("UNDER_REPAIR", SITE, SITE, "SCHEDULED", 1, 2));
        assertEquals("That resource is held at a different site than the cohort", ResourceBookingRules.bookProblem("IN_SERVICE", UUID.randomUUID(), SITE, "SCHEDULED", 1, 2));
        assertEquals("Book at least one", ResourceBookingRules.bookProblem("IN_SERVICE", SITE, SITE, "SCHEDULED", 0, 2));
        assertTrue(ResourceBookingRules.bookProblem("IN_SERVICE", SITE, SITE, "SCHEDULED", 3, 2).contains("holds 2"));
        assertNull(ResourceBookingRules.bookProblem("IN_SERVICE", SITE, SITE, "SCHEDULED", 2, 2));
    }

    @Test
    void aShareableResourceNeverClashes() {
        assertNull(ResourceBookingRules.clash("Projector", true, 1, 5, 1));
    }

    @Test
    void aClashIsWhenBookingsAtTheSameTimeExceedWhatTheSiteHolds() {
        assertNull(ResourceBookingRules.clash("Welding rig", false, 2, 1, 1));
        assertEquals("Welding rig: 2 of 2 already booked at that time, so only 0 left and 1 asked for", ResourceBookingRules.clash("Welding rig", false, 2, 2, 1));
        assertTrue(ResourceBookingRules.clash("Welding rig", false, 3, 1, 3).contains("only 2 left"));
    }

    @Test
    void releaseNeedsAReason() {
        assertTrue(ResourceBookingRules.releaseProblem("short").contains("at least 10"));
        assertTrue(ResourceBookingRules.releaseProblem(null).contains("at least 10"));
        assertNull(ResourceBookingRules.releaseProblem("The practical was moved"));
    }
}
