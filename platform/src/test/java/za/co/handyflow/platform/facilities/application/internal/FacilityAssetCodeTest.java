package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityAssetCodeTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void theQrPayloadRoundTrips() {
        FacilityAssetCode.Parsed p = FacilityAssetCode.parse(FacilityAssetCode.payloadFor(id));
        assertEquals(id, p.id());
        assertNull(p.tag());
    }

    @Test
    void aBareIdAndALinkEndingInTheIdAreAccepted() {
        assertEquals(id, FacilityAssetCode.parse("  " + id + " ").id());
        assertEquals(id, FacilityAssetCode.parse("https://app.example.co.za/facilities/assets/" + id).id());
        assertEquals(id, FacilityAssetCode.parse("hf:asset:" + id).id());
    }

    @Test
    void anythingElseIsTreatedAsTheAssetTag() {
        FacilityAssetCode.Parsed p = FacilityAssetCode.parse(" GEN-0042 ");
        assertNull(p.id());
        assertEquals("GEN-0042", p.tag());
    }

    @Test
    void aTagWithASlashUsesTheLastSegmentAndNeverFailsOnANonUuid() {
        assertEquals("0042", FacilityAssetCode.parse("GEN/0042").tag());
    }

    @Test
    void emptyOrOversizedCodesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FacilityAssetCode.parse(null));
        assertThrows(IllegalArgumentException.class, () -> FacilityAssetCode.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> FacilityAssetCode.parse("HF:ASSET:"));
        assertThrows(IllegalArgumentException.class, () -> FacilityAssetCode.parse("x".repeat(201)));
    }
}
