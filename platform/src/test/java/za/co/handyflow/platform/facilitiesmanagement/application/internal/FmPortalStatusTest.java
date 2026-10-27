package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FmPortalStatusTest {

    @Test
    void internalStatesArePresentedAsTheClientSeesThem() {
        assertEquals("IN_PROGRESS", FmPortalStatus.present("PENDING_VERIFICATION"));
        assertEquals("SUBMITTED", FmPortalStatus.present("REQUESTED"));
        assertEquals("DECLINED", FmPortalStatus.present("REJECTED"));
        assertEquals("COMPLETED", FmPortalStatus.present("COMPLETED"));
        assertNull(FmPortalStatus.present(null));
    }

    @Test
    void filteringByInProgressAlsoCoversWorkAwaitingVerification() {
        assertEquals(List.of("IN_PROGRESS", "PENDING_VERIFICATION"), FmPortalStatus.filter("in_progress"));
    }

    @Test
    void submittedAndDeclinedMapBackToTheInternalStatuses() {
        assertEquals(List.of("REQUESTED"), FmPortalStatus.filter("SUBMITTED"));
        assertEquals(List.of("REJECTED"), FmPortalStatus.filter("declined"));
    }

    @Test
    void internalOrUnknownFiltersMatchNothing() {
        assertEquals(List.of("NONE"), FmPortalStatus.filter("PENDING_VERIFICATION"));
        assertEquals(List.of("NONE"), FmPortalStatus.filter("REQUESTED"));
        assertEquals(List.of("NONE"), FmPortalStatus.filter("bogus"));
    }

    @Test
    void noFilterListsEverythingAClientMayEverSee() {
        assertEquals(FmPortalStatus.LISTABLE, FmPortalStatus.filter(null));
        assertEquals(FmPortalStatus.LISTABLE, FmPortalStatus.filter("  "));
    }
}
