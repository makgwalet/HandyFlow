package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SealedBytesTest {

    static SecretKeySpec key(int fill) { byte[] k = new byte[32]; Arrays.fill(k, (byte) fill); return new SecretKeySpec(k, "AES"); }
    static byte[] aad(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    byte[] plain = "%PDF-1.7 a small file".getBytes(StandardCharsets.UTF_8);

    @Test void aSealedFileOpensBackToTheSameBytes() {
        byte[] sealed = SealedBytes.seal(key(1), plain, aad("t|o"));
        assertTrue(SealedBytes.looksSealed(sealed));
        assertEquals(plain.length + SealedBytes.OVERHEAD, sealed.length);
        assertArrayEquals(plain, SealedBytes.open(key(1), sealed, aad("t|o")));
    }

    @Test void theSameFileSealsDifferentlyEachTime() {
        assertFalse(Arrays.equals(SealedBytes.seal(key(1), plain, null), SealedBytes.seal(key(1), plain, null)));
    }

    @Test void aChangedByteAnywhereIsRefused() {
        byte[] sealed = SealedBytes.seal(key(1), plain, aad("t|o"));
        for (int i = 4; i < sealed.length; i++) {
            byte[] bad = sealed.clone();
            bad[i] ^= 1;
            final byte[] b = bad;
            assertThrows(SealedBytes.OpenFailedException.class, () -> SealedBytes.open(key(1), b, aad("t|o")), "byte " + i);
        }
    }

    @Test void theWrongKeyOrOwnerIsRefused() {
        byte[] sealed = SealedBytes.seal(key(1), plain, aad("tenantA|obj1"));
        assertThrows(SealedBytes.OpenFailedException.class, () -> SealedBytes.open(key(2), sealed, aad("tenantA|obj1")));
        assertThrows(SealedBytes.OpenFailedException.class, () -> SealedBytes.open(key(1), sealed, aad("tenantB|obj1")));
        assertThrows(SealedBytes.OpenFailedException.class, () -> SealedBytes.open(key(1), sealed, aad("tenantA|obj2")));
    }

    @Test void plainOrTruncatedBytesAreNotTakenForSealed() {
        assertFalse(SealedBytes.looksSealed(plain));
        byte[] sealed = SealedBytes.seal(key(1), plain, null);
        byte[] cut = Arrays.copyOf(sealed, SealedBytes.OVERHEAD - 1);
        assertFalse(SealedBytes.looksSealed(cut));
        assertThrows(SealedBytes.OpenFailedException.class, () -> SealedBytes.open(key(1), cut, null));
        assertThrows(SealedBytes.OpenFailedException.class, () -> SealedBytes.open(key(1), plain, null));
    }

    @Test void anEmptyFileStillSealsAndOpens() {
        byte[] sealed = SealedBytes.seal(key(1), new byte[0], null);
        assertEquals(0, SealedBytes.open(key(1), sealed, null).length);
    }

    @Test void theFieldServiceSealsWithItsOwnKey() {
        String k = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        FieldEncryptionService a = new FieldEncryptionService(k);
        byte[] sealed = a.sealBytes(plain, aad("x"));
        assertArrayEquals(plain, a.openBytes(sealed, aad("x")));
        FieldEncryptionService other = new FieldEncryptionService(java.util.Base64.getEncoder().encodeToString(key(9).getEncoded()));
        assertThrows(SealedBytes.OpenFailedException.class, () -> other.openBytes(sealed, aad("x")));
    }
}
