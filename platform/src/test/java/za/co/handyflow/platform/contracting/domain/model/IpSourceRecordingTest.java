package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A stored address says where it came from; older rows without a source stay unknown rather than guessed. */
class IpSourceRecordingTest {
    TenantId tenant = TenantId.generate();

    @Test
    void aSignatureKeepsTheSourceOfItsAddress() {
        ContractSignature s = ContractSignature.create(tenant, UUID.randomUUID(), UUID.randomUUID(), "h", "1234", "198.51.100.7", "agent", null, null);
        assertNull(s.getIpSource(), "unknown until recorded");
        s.recordIpSource("DIRECT");
        assertEquals("DIRECT", s.getIpSource());
    }

    @Test
    void aSignedPartyKeepsTheSourceOfItsAddress() {
        ContractParty p = ContractParty.create(tenant, UUID.randomUUID(), "INDIVIDUAL", "CLIENT", "A", "a@x.co", "0820000000", null, 1);
        p.markSigned("203.0.113.9", "agent");
        p.recordSignIpSource("TRUSTED_PROXY");
        assertEquals("203.0.113.9", p.getSignIpAddress());
        assertEquals("TRUSTED_PROXY", p.getSignIpSource());
    }

    @Test
    void negotiationEventsAndSessionsKeepTheSource() {
        ContractNegotiationEvent e = ContractNegotiationEvent.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "COUNTERPARTY", null, null, "PROPOSED", "x", "203.0.113.9")
                .withIpSource("TRUSTED_PROXY");
        assertEquals("TRUSTED_PROXY", e.getIpSource());
        ContractNegotiationSession s = ContractNegotiationSession.start(UUID.randomUUID(), UUID.randomUUID(), "hash", java.time.Instant.now().plusSeconds(60), "198.51.100.7")
                .withIpSource("DIRECT");
        assertEquals("DIRECT", s.getIpSource());
    }
}
