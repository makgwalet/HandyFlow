package za.co.handyflow.platform.shared;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM for whole files, in a small self-describing envelope. Plain JDK, no Spring, so it can be tested on its own.
 * <p>
 * Envelope: {@code 'H' 'F' 'S' '1'} (4 bytes) | IV (12 bytes) | ciphertext with the 16-byte GCM tag.
 * The caller passes associated data (AAD) that is authenticated but not stored: the document store uses the tenant and the
 * registry row id, so a sealed file copied onto another row or another tenant fails to open instead of being served as theirs.
 * A flipped bit anywhere, a wrong key or wrong AAD all fail the tag check and throw {@link OpenFailedException}.
 */
public final class SealedBytes {
    private SealedBytes() {}

    static final byte[] MAGIC = {'H', 'F', 'S', '1'};
    static final int IV_LENGTH = 12;
    static final int TAG_BITS = 128;
    public static final int OVERHEAD = MAGIC.length + IV_LENGTH + TAG_BITS / 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Thrown when a sealed file cannot be opened: tampered, truncated, wrong key or wrong associated data. */
    public static final class OpenFailedException extends RuntimeException {
        public OpenFailedException(String message, Throwable cause) { super(message, cause); }
    }

    public static boolean looksSealed(byte[] b) {
        return b != null && b.length >= OVERHEAD && Arrays.equals(Arrays.copyOf(b, MAGIC.length), MAGIC);
    }

    public static byte[] seal(SecretKeySpec key, byte[] plain, byte[] aad) {
        if (plain == null) throw new IllegalArgumentException("Nothing to seal");
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) c.updateAAD(aad);
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[MAGIC.length + IV_LENGTH + ct.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            System.arraycopy(iv, 0, out, MAGIC.length, IV_LENGTH);
            System.arraycopy(ct, 0, out, MAGIC.length + IV_LENGTH, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("File encryption failed", e);
        }
    }

    public static byte[] open(SecretKeySpec key, byte[] sealed, byte[] aad) {
        if (!looksSealed(sealed)) throw new OpenFailedException("Not a sealed file, or it is truncated", null);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, MAGIC.length, IV_LENGTH));
            if (aad != null) c.updateAAD(aad);
            int off = MAGIC.length + IV_LENGTH;
            return c.doFinal(sealed, off, sealed.length - off);
        } catch (AEADBadTagException e) {
            throw new OpenFailedException("The sealed file failed its integrity check (changed, wrong key or wrong owner)", e);
        } catch (GeneralSecurityException e) {
            throw new OpenFailedException("The sealed file could not be opened", e);
        }
    }
}
