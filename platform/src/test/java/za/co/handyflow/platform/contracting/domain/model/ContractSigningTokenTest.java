package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractSigningTokenTest {

    @Test
    void storesOnlyTheHashNeverTheRawToken() throws Exception {
        String raw = "raw-token-value";
        UUID issuer = UUID.randomUUID();
        ContractSigningToken t = ContractSigningToken.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                raw, Instant.now().plusSeconds(3600), issuer);

        assertNull(t.getToken(), "the raw token must not be kept");
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, t.getTokenHash());
        assertEquals(issuer, t.getIssuedBy());
        assertTrue(t.isValid());
    }

    @Test
    void revokedOrExpiredTokenIsNotValid() {
        ContractSigningToken revoked = ContractSigningToken.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "a", Instant.now().plusSeconds(3600), null);
        revoked.setRevokedAt(Instant.now());
        assertFalse(revoked.isValid());

        ContractSigningToken expired = ContractSigningToken.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "b", Instant.now().minusSeconds(1), null);
        assertFalse(expired.isValid());
    }

    @Test
    void recordsDeliveryStatus() {
        ContractSigningToken t = ContractSigningToken.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "c", Instant.now().plusSeconds(3600), null);
        t.recordDelivery("FAILED");
        assertEquals("FAILED", t.getDeliveryStatus());
        assertNotNull(t.getDeliveryAttemptedAt());
    }
}
