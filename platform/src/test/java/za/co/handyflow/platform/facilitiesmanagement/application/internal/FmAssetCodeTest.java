package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmAssetCodeTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void theQrPayloadRoundTrips() {
        FmAssetCode.Parsed p = FmAssetCode.parse(FmAssetCode.payloadFor(id));
        assertEquals(id, p.id());
        assertNull(p.tag());
    }

    @Test
    void aBareIdAndALinkEndingInTheIdAreAccepted() {
        assertEquals(id, FmAssetCode.parse("  " + id + " ").id());
        assertEquals(id, FmAssetCode.parse("https://app.example.co.za/facilities/assets/" + id).id());
        assertEquals(id, FmAssetCode.parse("hf:fmasset:" + id).id());
    }

    @Test
    void anythingElseIsTreatedAsTheAssetTag() {
        FmAssetCode.Parsed p = FmAssetCode.parse(" GEN-0042 ");
        assertNull(p.id());
        assertEquals("GEN-0042", p.tag());
    }

    @Test
    void aTagWithASlashUsesTheLastSegmentAndNeverFailsOnANonUuid() {
        assertEquals("0042", FmAssetCode.parse("GEN/0042").tag());
    }

    @Test
    void emptyOrOversizedCodesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FmAssetCode.parse(null));
        assertThrows(IllegalArgumentException.class, () -> FmAssetCode.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> FmAssetCode.parse("HF:FMASSET:"));
        assertThrows(IllegalArgumentException.class, () -> FmAssetCode.parse("x".repeat(201)));
    }
}
